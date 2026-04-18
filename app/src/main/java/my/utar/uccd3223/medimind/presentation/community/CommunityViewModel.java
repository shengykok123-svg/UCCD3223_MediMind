package my.utar.uccd3223.medimind.presentation.community;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;
import android.util.Log;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;
import my.utar.uccd3223.medimind.MediMindApplication;
import my.utar.uccd3223.medimind.MainActivity;
import my.utar.uccd3223.medimind.R;
import my.utar.uccd3223.medimind.domain.repository.MedicationRepository;

@HiltViewModel
public class CommunityViewModel extends ViewModel {

    private final FirebaseFirestore firestore;
    private final MedicationRepository repository;
    private final Executor executor;
    private final Context context;

    private final MutableLiveData<List<CommunityMember>> members = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<FriendRequest>> pendingRequests = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> unreadCount = new MutableLiveData<>(0);
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>();
    private final MutableLiveData<String> toastMessage = new MutableLiveData<>();

    private ListenerRegistration membersListener;
    private ListenerRegistration requestsListener;
    private ListenerRegistration currentUserListener;
    private final Map<String, ListenerRegistration> adherenceListeners = new HashMap<>();
    private final Map<String, ListenerRegistration> userListeners = new HashMap<>();
    private final Map<String, CommunityMember> memberState = new LinkedHashMap<>();
    private AdherenceSyncHelper syncHelper;
    private boolean listenersAttached = false;

    private String currentUid;
    private String currentUserName;
    private String currentShareableId;
    private String currentProfileImage;

    // Track known request IDs to detect new ones for push notification
    private final Set<String> knownRequestIds = new HashSet<>();
    private boolean initialLoadDone = false;

    @Inject
    public CommunityViewModel(MedicationRepository repository, Executor executor,
                              Context context, FirebaseFirestore firestore) {
        this.repository = repository;
        this.executor = executor;
        this.context = context;
        this.firestore = firestore;

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            currentUid = user.getUid();
            syncHelper = new AdherenceSyncHelper(repository);
            attachCurrentUserListener();
        }
    }

    // ─── LiveData Getters ─────────────────────────────────────────

    public LiveData<List<CommunityMember>> getMembers() { return members; }
    public LiveData<List<FriendRequest>> getPendingRequests() { return pendingRequests; }
    public LiveData<Integer> getUnreadCount() { return unreadCount; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getToastMessage() { return toastMessage; }

    public void clearToastMessage() {
        toastMessage.setValue(null);
    }

    // ─── Initialization ──────────────────────────────────────────

    private void attachCurrentUserListener() {
        if (currentUid == null || currentUserListener != null) return;

        currentUserListener = firestore.collection("users").document(currentUid)
                .addSnapshotListener((doc, e) -> {
                    if (e != null) {
                        error.postValue("Failed to load user info");
                        return;
                    }
                    if (doc == null || !doc.exists()) return;

                    currentUserName = doc.getString("name");
                    currentShareableId = doc.getString("shareableId");
                    currentProfileImage = doc.getString("profileImagePath");

                    CommunityMember self;
                    synchronized (memberState) {
                        self = memberState.get(currentUid);
                    }
                    if (self != null) {
                        self.setName(currentUserName);
                        self.setShareableId(currentShareableId);
                        self.setProfileImagePath(currentProfileImage);
                        self.setSelf(true);
                        synchronized (memberState) {
                            memberState.put(currentUid, self);
                        }
                        publishMembers();
                    }

                    attachListeners();
                });
    }

    private void attachListeners() {
        if (listenersAttached) return;
        listenersAttached = true;
        attachMembersListener();
        attachRequestsListener();
    }

    public void refreshCommunityData() {
        if (currentUid == null) return;
        if (currentUserListener == null) {
            attachCurrentUserListener();
        }
        attachListeners();
        loadMemberAdherence();
        publishMembers();
    }

    // ─── Members Listener ────────────────────────────────────────

    private void attachMembersListener() {
        if (currentUid == null) return;

        membersListener = firestore.collection("users").document(currentUid)
                .collection("community_members")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;
                    observeCommunityMembers(snapshots);
                });
    }

    public void loadMemberAdherence() {
        if (currentUid == null || syncHelper == null) return;
        isLoading.postValue(true);

        syncHelper.syncTodayAdherence(new AdherenceSyncHelper.SyncCallback() {
            @Override
            public void onSynced(int takenCount, int pendingCount, int missedCount, int totalCount) {
                CommunityMember self = new CommunityMember(currentUid, currentUserName, currentShareableId, true);
                self.setTakenCount(takenCount);
                self.setPendingCount(pendingCount);
                self.setMissedCount(missedCount);
                self.setTotalCount(totalCount);
                self.setProfileImagePath(currentProfileImage);

                synchronized (memberState) {
                    memberState.put(currentUid, self);
                }
                publishMembers();
                isLoading.postValue(false);
            }

            @Override
            public void onError(String message) {
                error.postValue(message);
                isLoading.postValue(false);
            }
        });
    }

    private void observeCommunityMembers(com.google.firebase.firestore.QuerySnapshot snapshots) {
        if (currentUid == null) return;

        Set<String> activeUids = new HashSet<>();
        activeUids.add(currentUid);

        if (snapshots != null) {
            for (QueryDocumentSnapshot doc : snapshots) {
                String memberUid = doc.getId();
                activeUids.add(memberUid);

                CommunityMember existing;
                synchronized (memberState) {
                    existing = memberState.get(memberUid);
                }
                if (existing == null) {
                    existing = new CommunityMember(memberUid, doc.getString("name"), doc.getString("shareableId"), false);
                } else {
                    existing.setName(doc.getString("name"));
                    existing.setShareableId(doc.getString("shareableId"));
                    existing.setSelf(false);
                }
                existing.setCustomTitle(doc.getString("customTitle"));

                final CommunityMember member = existing;
                synchronized (memberState) {
                    memberState.put(memberUid, member);
                }
                attachMemberUserListener(memberUid, member);
                attachMemberAdherenceListener(memberUid, member);
            }
        }

        removeInactiveMemberListeners(activeUids);
        publishMembers();
        loadMemberAdherence();
    }

    private void attachMemberAdherenceListener(String memberUid, CommunityMember member) {
        if (adherenceListeners.containsKey(memberUid)) return;

        String today = LocalDate.now().toString();
        ListenerRegistration registration = firestore.collection("users").document(memberUid)
                .collection("daily_adherence").document(today)
                .addSnapshotListener((adherenceDoc, err) -> {
                    if (err != null) return;

                    applyAdherenceSnapshot(member, adherenceDoc);
                    synchronized (memberState) {
                        memberState.put(memberUid, member);
                    }
                    publishMembers();
                });

        adherenceListeners.put(memberUid, registration);
    }

    private void removeInactiveMemberListeners(Set<String> activeUids) {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, ListenerRegistration> entry : adherenceListeners.entrySet()) {
            if (!activeUids.contains(entry.getKey())) {
                entry.getValue().remove();
                toRemove.add(entry.getKey());
            }
        }

        for (String uid : toRemove) {
            adherenceListeners.remove(uid);
            ListenerRegistration userRegistration = userListeners.remove(uid);
            if (userRegistration != null) userRegistration.remove();
            synchronized (memberState) {
                memberState.remove(uid);
            }
        }
    }

    private void attachMemberUserListener(String memberUid, CommunityMember member) {
        if (userListeners.containsKey(memberUid)) return;

        ListenerRegistration registration = firestore.collection("users").document(memberUid)
                .addSnapshotListener((userDoc, err) -> {
                    if (err != null || userDoc == null || !userDoc.exists()) return;

                    member.setProfileImagePath(userDoc.getString("profileImagePath"));
                    String latestName = userDoc.getString("name");
                    if (!TextUtils.isEmpty(latestName)) {
                        member.setName(latestName);
                    }
                    String latestShareableId = userDoc.getString("shareableId");
                    if (!TextUtils.isEmpty(latestShareableId)) {
                        member.setShareableId(latestShareableId);
                    }
                    synchronized (memberState) {
                        memberState.put(memberUid, member);
                    }
                    publishMembers();
                });

        userListeners.put(memberUid, registration);
    }

    private void publishMembers() {
        List<CommunityMember> snapshot;
        synchronized (memberState) {
            snapshot = new ArrayList<>();
            for (CommunityMember member : memberState.values()) {
                if (member != null && !member.isSelf()) {
                    snapshot.add(member);
                }
            }
        }
        members.postValue(snapshot);
    }

    private void applyAdherenceSnapshot(CommunityMember member, DocumentSnapshot adherenceDoc) {
        int taken = 0;
        int missed = 0;
        int pending = 0;
        int total = 0;

        if (adherenceDoc != null && adherenceDoc.exists()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> medications = (List<Map<String, Object>>) adherenceDoc.get("medications");

            if (medications != null && !medications.isEmpty()) {
                for (Map<String, Object> med : medications) {
                    String status = getStringValue(med, "status", "todayStatus");
                    total++;
                    if ("TAKEN".equalsIgnoreCase(status)) {
                        taken++;
                    } else if ("MISSED".equalsIgnoreCase(status)) {
                        missed++;
                    } else {
                        pending++;
                    }
                }
            } else {
                taken = getIntValue(adherenceDoc, "takenCount", "taken");
                missed = getIntValue(adherenceDoc, "missedCount", "missed");
                pending = getIntValue(adherenceDoc, "pendingCount", "pending");
                total = getIntValue(adherenceDoc, "totalCount", "totalScheduled", "scheduledCount");

                if (total <= 0) {
                    total = taken + missed + pending;
                }

                int recomputedPending = total - taken - missed;
                if (recomputedPending >= 0 && recomputedPending != pending) {
                    pending = recomputedPending;
                }
            }
        }

        member.setTakenCount(taken);
        member.setMissedCount(missed);
        member.setPendingCount(pending);
        member.setTotalCount(total);
    }

    private int getIntValue(DocumentSnapshot doc, String... keys) {
        if (doc == null) return 0;
        for (String key : keys) {
            Long value = doc.getLong(key);
            if (value != null) return value.intValue();
            Number number = doc.getDouble(key);
            if (number != null) return number.intValue();
        }
        return 0;
    }

    private String getStringValue(Map<String, Object> map, String... keys) {
        if (map == null) return null;
        for (String key : keys) {
            Object value = map.get(key);
            if (value != null) return String.valueOf(value);
        }
        return null;
    }

    // ─── Requests Listener ───────────────────────────────────────

    private void attachRequestsListener() {
        if (currentUid == null) return;

        requestsListener = firestore.collection("friend_requests")
                .whereEqualTo("toUid", currentUid)
                .whereEqualTo("status", "pending")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;
                    if (snapshots == null) return;

                    List<FriendRequest> requests = new ArrayList<>();
                    int unread = 0;

                    for (QueryDocumentSnapshot doc : snapshots) {
                        FriendRequest request = new FriendRequest();
                        request.setId(doc.getId());
                        request.setFromUid(doc.getString("fromUid"));
                        request.setToUid(doc.getString("toUid"));
                        request.setFromName(doc.getString("fromName"));
                        request.setToName(doc.getString("toName"));
                        request.setMessage(doc.getString("message"));
                        request.setStatus(doc.getString("status"));
                        com.google.firebase.Timestamp ts = doc.getTimestamp("timestamp");
                        request.setTimestamp(ts != null ? ts.toDate().getTime() : 0);
                        Boolean readVal = doc.getBoolean("read");
                        request.setRead(readVal != null && readVal);

                        requests.add(request);

                        if (!request.isRead()) {
                            unread++;
                        }

                        // Push notification for new requests
                        if (initialLoadDone && !knownRequestIds.contains(doc.getId())) {
                            showFriendRequestNotification(request);
                        }
                        knownRequestIds.add(doc.getId());
                    }

                    initialLoadDone = true;
                    pendingRequests.postValue(requests);
                    unreadCount.postValue(unread);
                });
    }

    // ─── Send Friend Request ─────────────────────────────────────

    public void sendFriendRequest(String shareableId, String message) {
        if (currentUid == null) return;

        String trimmedId = shareableId.trim();
        if (trimmedId.isEmpty()) {
            toastMessage.postValue("Please enter a member ID");
            return;
        }

        // Check if trying to add self
        if (trimmedId.equals(currentShareableId)) {
            toastMessage.postValue(context.getString(R.string.cannot_add_self));
            return;
        }

        isLoading.postValue(true);

        // Find user by shareable ID
        firestore.collection("users")
                .whereEqualTo("shareableId", trimmedId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        toastMessage.postValue(context.getString(R.string.user_not_found));
                        isLoading.postValue(false);
                        return;
                    }

                    DocumentSnapshot targetDoc = querySnapshot.getDocuments().get(0);
                    String targetUid = targetDoc.getId();
                    String targetName = targetDoc.getString("name");

                    // Check if already a member
                    firestore.collection("users").document(currentUid)
                            .collection("community_members").document(targetUid).get()
                            .addOnSuccessListener(memberDoc -> {
                                if (memberDoc.exists()) {
                                    toastMessage.postValue(context.getString(R.string.already_member));
                                    isLoading.postValue(false);
                                    return;
                                }

                                // Check for existing pending request
                                firestore.collection("friend_requests")
                                        .whereEqualTo("fromUid", currentUid)
                                        .whereEqualTo("toUid", targetUid)
                                        .whereEqualTo("status", "pending")
                                        .get()
                                        .addOnSuccessListener(existingRequests -> {
                                            if (!existingRequests.isEmpty()) {
                                                toastMessage.postValue(context.getString(R.string.request_already_sent));
                                                isLoading.postValue(false);
                                                return;
                                            }

                                            // Create the friend request
                                            Map<String, Object> requestData = new HashMap<>();
                                            requestData.put("fromUid", currentUid);
                                            requestData.put("toUid", targetUid);
                                            requestData.put("fromName", currentUserName);
                                            requestData.put("toName", targetName);
                                            requestData.put("message", message != null ? message.trim() : "");
                                            requestData.put("status", "pending");
                                            requestData.put("timestamp", FieldValue.serverTimestamp());
                                            requestData.put("read", false);

                                            firestore.collection("friend_requests")
                                                    .add(requestData)
                                                    .addOnSuccessListener(docRef -> {
                                                        toastMessage.postValue(context.getString(R.string.request_sent));
                                                        isLoading.postValue(false);
                                                    })
                                                    .addOnFailureListener(err -> {
                                                        error.postValue("Failed to send request: " + err.getMessage());
                                                        isLoading.postValue(false);
                                                    });
                                        })
                                        .addOnFailureListener(err -> {
                                            error.postValue("Error checking requests: " + err.getMessage());
                                            isLoading.postValue(false);
                                        });
                            })
                            .addOnFailureListener(err -> {
                                error.postValue("Error checking members: " + err.getMessage());
                                isLoading.postValue(false);
                            });
                })
                .addOnFailureListener(err -> {
                    error.postValue("Error finding user: " + err.getMessage());
                    isLoading.postValue(false);
                });
    }

    // ─── Accept Request ──────────────────────────────────────────

    public void acceptRequest(FriendRequest request) {
        if (currentUid == null) return;

        WriteBatch batch = firestore.batch();

        // Update request status
        batch.update(firestore.collection("friend_requests").document(request.getId()),
                "status", "accepted", "read", true);

        // Add to current user's community
        Map<String, Object> memberData = new HashMap<>();
        memberData.put("name", request.getFromName());
        memberData.put("shareableId", "");
        memberData.put("addedAt", FieldValue.serverTimestamp());

        batch.set(firestore.collection("users").document(currentUid)
                .collection("community_members").document(request.getFromUid()), memberData);

        // Add current user to sender's community (bidirectional)
        Map<String, Object> reverseMemberData = new HashMap<>();
        reverseMemberData.put("name", currentUserName);
        reverseMemberData.put("shareableId", currentShareableId != null ? currentShareableId : "");
        reverseMemberData.put("addedAt", FieldValue.serverTimestamp());

        batch.set(firestore.collection("users").document(request.getFromUid())
                .collection("community_members").document(currentUid), reverseMemberData);

        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    toastMessage.postValue(context.getString(R.string.request_accepted));
                    // Fetch the sender's shareable ID and update
                    firestore.collection("users").document(request.getFromUid()).get()
                            .addOnSuccessListener(doc -> {
                                if (doc.exists()) {
                                    String senderShareableId = doc.getString("shareableId");
                                    if (senderShareableId != null) {
                                        firestore.collection("users").document(currentUid)
                                                .collection("community_members").document(request.getFromUid())
                                                .update("shareableId", senderShareableId);
                                    }
                                }
                            });
                    loadMemberAdherence();
                })
                .addOnFailureListener(err ->
                        error.postValue("Failed to accept request: " + err.getMessage()));
    }

    // ─── Ignore Request ──────────────────────────────────────────

    public void ignoreRequest(FriendRequest request) {
        firestore.collection("friend_requests").document(request.getId())
                .update("status", "ignored", "read", true)
                .addOnSuccessListener(aVoid ->
                        toastMessage.postValue(context.getString(R.string.request_ignored)))
                .addOnFailureListener(err ->
                        error.postValue("Failed to ignore request: " + err.getMessage()));
    }

    public void updateMemberCustomTitle(String memberUid, String customTitle) {
        if (currentUid == null || memberUid == null || memberUid.isEmpty()) return;

        String normalizedTitle = customTitle != null ? customTitle.trim() : "";
        String previousTitle = null;
        synchronized (memberState) {
            CommunityMember member = memberState.get(memberUid);
            if (member != null) {
                previousTitle = member.getCustomTitle();
                member.setCustomTitle(normalizedTitle);
                memberState.put(memberUid, member);
            }
        }
        publishMembers();
        final String rollbackTitle = previousTitle;

        Map<String, Object> updates = new HashMap<>();
        updates.put("customTitle", normalizedTitle);

        firestore.collection("users").document(currentUid)
                .collection("community_members").document(memberUid)
                .update(updates)
                .addOnSuccessListener(aVoid -> toastMessage.postValue("Title updated"))
                .addOnFailureListener(err -> {
                    synchronized (memberState) {
                        CommunityMember member = memberState.get(memberUid);
                        if (member != null) {
                            member.setCustomTitle(rollbackTitle);
                            memberState.put(memberUid, member);
                        }
                    }
                    publishMembers();
                    error.postValue("Failed to update title: " + err.getMessage());
                });
    }

    // ─── Mark Requests Read ──────────────────────────────────────

    public void markRequestsRead() {
        List<FriendRequest> requests = pendingRequests.getValue();
        if (requests == null || requests.isEmpty()) return;

        WriteBatch batch = firestore.batch();
        boolean hasUnread = false;

        for (FriendRequest request : requests) {
            if (!request.isRead()) {
                batch.update(firestore.collection("friend_requests").document(request.getId()),
                        "read", true);
                hasUnread = true;
            }
        }

        if (hasUnread) {
            batch.commit();
        }
    }

    // ─── Get Member Medications ──────────────────────────────────

    public interface MedicationListCallback {
        void onResult(List<Map<String, Object>> medications, int taken, int pending, int missed);
        void onError(String message);
    }

    public ListenerRegistration observeMemberMedications(String memberUid, MedicationListCallback callback) {
        String today = LocalDate.now().toString();

        return firestore.collection("users").document(memberUid)
                .collection("daily_adherence").document(today)
                .addSnapshotListener((doc, e) -> {
                    if (e != null) {
                        callback.onError(e.getMessage() != null ? e.getMessage() : "Failed to load medication history");
                        return;
                    }

                    if (doc == null || !doc.exists()) {
                        callback.onResult(new ArrayList<>(), 0, 0, 0);
                        return;
                    }

                    CommunityMember tempMember = new CommunityMember();
                    applyAdherenceSnapshot(tempMember, doc);

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> medications = (List<Map<String, Object>>) doc.get("medications");
                    if (medications == null) medications = new ArrayList<>();

                    callback.onResult(medications,
                            tempMember.getTakenCount(),
                            tempMember.getPendingCount(),
                            tempMember.getMissedCount());
                });
    }

    public void getMemberMedications(String memberUid, MedicationListCallback callback) {
        String today = LocalDate.now().toString();

        firestore.collection("users").document(memberUid)
                .collection("daily_adherence").document(today).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onResult(new ArrayList<>(), 0, 0, 0);
                        return;
                    }

                    CommunityMember tempMember = new CommunityMember();
                    applyAdherenceSnapshot(tempMember, doc);

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> medications = (List<Map<String, Object>>) doc.get("medications");
                    if (medications == null) medications = new ArrayList<>();

                    callback.onResult(medications,
                            tempMember.getTakenCount(),
                            tempMember.getPendingCount(),
                            tempMember.getMissedCount());
                })
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    // ─── Push Notification ───────────────────────────────────────

    private void showFriendRequestNotification(FriendRequest request) {
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = context.getString(R.string.community_notifications);
        String body = context.getString(R.string.new_friend_request,
                request.getFromName() != null ? request.getFromName() : "Someone");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MediMindApplication.COMMUNITY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_community)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        notificationManager.notify(request.getId().hashCode(), builder.build());
    }

    // ─── Cleanup ─────────────────────────────────────────────────

    @Override
    protected void onCleared() {
        super.onCleared();
        if (membersListener != null) membersListener.remove();
        if (requestsListener != null) requestsListener.remove();
        if (currentUserListener != null) currentUserListener.remove();
        for (ListenerRegistration registration : adherenceListeners.values()) {
            registration.remove();
        }
        for (ListenerRegistration registration : userListeners.values()) {
            registration.remove();
        }
        adherenceListeners.clear();
        userListeners.clear();
    }
}

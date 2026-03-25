package my.utar.uccd3223.medimind.presentation.community;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

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
    private AdherenceSyncHelper syncHelper;

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
            loadCurrentUserInfo();
        }
    }

    // ─── LiveData Getters ─────────────────────────────────────────

    public LiveData<List<CommunityMember>> getMembers() { return members; }
    public LiveData<List<FriendRequest>> getPendingRequests() { return pendingRequests; }
    public LiveData<Integer> getUnreadCount() { return unreadCount; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getError() { return error; }
    public LiveData<String> getToastMessage() { return toastMessage; }

    // ─── Initialization ──────────────────────────────────────────

    private void loadCurrentUserInfo() {
        firestore.collection("users").document(currentUid).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        currentUserName = doc.getString("name");
                        currentShareableId = doc.getString("shareableId");
                        currentProfileImage = doc.getString("profileImagePath");
                        attachListeners();
                    }
                })
                .addOnFailureListener(e -> error.postValue("Failed to load user info"));
    }

    private void attachListeners() {
        attachMembersListener();
        attachRequestsListener();
    }

    // ─── Members Listener ────────────────────────────────────────

    private void attachMembersListener() {
        if (currentUid == null) return;

        membersListener = firestore.collection("users").document(currentUid)
                .collection("community_members")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) return;
                    loadMemberAdherence();
                });
    }

    public void loadMemberAdherence() {
        if (currentUid == null) return;
        isLoading.postValue(true);

        // First sync own data
        syncHelper.syncTodayAdherence(new AdherenceSyncHelper.SyncCallback() {
            @Override
            public void onSynced(int takenCount, int pendingCount, int missedCount, int totalCount) {
                // Build self member
                CommunityMember self = new CommunityMember(currentUid, currentUserName, currentShareableId, true);
                self.setTakenCount(takenCount);
                self.setPendingCount(pendingCount);
                self.setMissedCount(missedCount);
                self.setTotalCount(totalCount);
                self.setProfileImagePath(currentProfileImage);

                // Now load community members
                firestore.collection("users").document(currentUid)
                        .collection("community_members").get()
                        .addOnSuccessListener(snapshots -> {
                            List<CommunityMember> memberList = new ArrayList<>();
                            memberList.add(self);

                            if (snapshots.isEmpty()) {
                                members.postValue(memberList);
                                isLoading.postValue(false);
                                return;
                            }

                            final int[] remaining = {snapshots.size()};

                            for (QueryDocumentSnapshot doc : snapshots) {
                                String memberUid = doc.getId();
                                String memberName = doc.getString("name");
                                String memberShareableId = doc.getString("shareableId");

                                CommunityMember member = new CommunityMember(memberUid, memberName, memberShareableId, false);

                                // Load member's profile image
                                firestore.collection("users").document(memberUid).get()
                                        .addOnSuccessListener(userDoc -> {
                                            if (userDoc.exists()) {
                                                member.setProfileImagePath(userDoc.getString("profileImagePath"));
                                            }

                                            // Load adherence data
                                            String today = LocalDate.now().toString();
                                            firestore.collection("users").document(memberUid)
                                                    .collection("daily_adherence").document(today).get()
                                                    .addOnSuccessListener(adherenceDoc -> {
                                                        if (adherenceDoc.exists()) {
                                                            Long taken = adherenceDoc.getLong("takenCount");
                                                            Long missed = adherenceDoc.getLong("missedCount");
                                                            Long pending = adherenceDoc.getLong("pendingCount");
                                                            Long total = adherenceDoc.getLong("totalCount");
                                                            member.setTakenCount(taken != null ? taken.intValue() : 0);
                                                            member.setMissedCount(missed != null ? missed.intValue() : 0);
                                                            member.setPendingCount(pending != null ? pending.intValue() : 0);
                                                            member.setTotalCount(total != null ? total.intValue() : 0);
                                                        }
                                                        memberList.add(member);
                                                        remaining[0]--;
                                                        if (remaining[0] == 0) {
                                                            members.postValue(memberList);
                                                            isLoading.postValue(false);
                                                        }
                                                    })
                                                    .addOnFailureListener(err -> {
                                                        memberList.add(member);
                                                        remaining[0]--;
                                                        if (remaining[0] == 0) {
                                                            members.postValue(memberList);
                                                            isLoading.postValue(false);
                                                        }
                                                    });
                                        })
                                        .addOnFailureListener(err -> {
                                            memberList.add(member);
                                            remaining[0]--;
                                            if (remaining[0] == 0) {
                                                members.postValue(memberList);
                                                isLoading.postValue(false);
                                            }
                                        });
                            }
                        })
                        .addOnFailureListener(err -> {
                            List<CommunityMember> memberList = new ArrayList<>();
                            memberList.add(self);
                            members.postValue(memberList);
                            isLoading.postValue(false);
                        });
            }

            @Override
            public void onError(String message) {
                // Even if sync fails, still try to show cached data
                CommunityMember self = new CommunityMember(currentUid, currentUserName, currentShareableId, true);
                self.setProfileImagePath(currentProfileImage);
                List<CommunityMember> memberList = new ArrayList<>();
                memberList.add(self);
                members.postValue(memberList);
                isLoading.postValue(false);
            }
        });
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

    public void getMemberMedications(String memberUid, MedicationListCallback callback) {
        String today = LocalDate.now().toString();

        firestore.collection("users").document(memberUid)
                .collection("daily_adherence").document(today).get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        callback.onResult(new ArrayList<>(), 0, 0, 0);
                        return;
                    }

                    Long taken = doc.getLong("takenCount");
                    Long pending = doc.getLong("pendingCount");
                    Long missed = doc.getLong("missedCount");

                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> medications = (List<Map<String, Object>>) doc.get("medications");
                    if (medications == null) medications = new ArrayList<>();

                    callback.onResult(medications,
                            taken != null ? taken.intValue() : 0,
                            pending != null ? pending.intValue() : 0,
                            missed != null ? missed.intValue() : 0);
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
    }
}

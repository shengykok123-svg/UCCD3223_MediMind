package my.utar.uccd3223.medimind.presentation.community;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.google.firebase.firestore.ListenerRegistration;
import java.util.Map;

import my.utar.uccd3223.medimind.R;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Displays community members, friend requests, notifications, and member detail dialogs.
 */
@AndroidEntryPoint
public class CommunityFragment extends Fragment {

    private CommunityViewModel viewModel;
    private CommunityMemberAdapter memberAdapter;

    // Header views
    private View btnNotifications;
    private TextView badgeCount;
    private TextView textFriendsCount;
    private RecyclerView recyclerMembers;

    /**
     * Inflates the community screen that displays shared members and the add-member card.
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_community, container, false);
    }

    /**
     * Connects the activity-scoped ViewModel, initializes controls, and observes community data.
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get ViewModel scoped to activity for persistence across tab switches
        viewModel = new ViewModelProvider(requireActivity()).get(CommunityViewModel.class);

        // Find views
        btnNotifications = view.findViewById(R.id.btn_notifications);
        badgeCount = view.findViewById(R.id.badge_count);
        textFriendsCount = view.findViewById(R.id.text_friends_count);
        recyclerMembers = view.findViewById(R.id.recycler_members);

        setupRecyclerView();
        setupSearch(view);
        setupNotificationBadge();
        View emptyAddButton = view.findViewById(R.id.btn_empty_add_member);
        if (emptyAddButton != null) {
            emptyAddButton.setOnClickListener(v -> showAddMemberDialog());
        }
        observeViewModel();

        // Initial refresh
        viewModel.refreshCommunityData();
    }

    // ─── RecyclerView Setup ──────────────────────────────────────

    private void setupRecyclerView() {
        memberAdapter = new CommunityMemberAdapter(new CommunityMemberAdapter.OnMemberClickListener() {
            @Override
            public void onMemberClick(CommunityMember member) {
                showMemberDetailDialog(member);
            }

            @Override
            public void onAddMemberClick() {
                showAddMemberDialog();
            }
        });

        recyclerMembers.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recyclerMembers.setAdapter(memberAdapter);
    }

    // ─── Search ──────────────────────────────────────────────────

    private void setupSearch(View view) {
        EditText inputSearch = view.findViewById(R.id.input_search);
        inputSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                memberAdapter.filter(s.toString());
            }
        });
    }

    // ─── Notification Badge ──────────────────────────────────────

    private void setupNotificationBadge() {
        btnNotifications.setOnClickListener(v -> showNotificationsDialog());
    }

    // ─── Observers ───────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.getMembers().observe(getViewLifecycleOwner(), members -> {
            if (members != null) {
                memberAdapter.setMembers(members);
                textFriendsCount.setText(getString(R.string.friends_count_format, members.size()));
                View emptyState = requireView().findViewById(R.id.empty_state);
                recyclerMembers.setVisibility(members.isEmpty() ? View.GONE : View.VISIBLE);
                emptyState.setVisibility(members.isEmpty() ? View.VISIBLE : View.GONE);
                animateCommunityState(members.isEmpty() ? emptyState : recyclerMembers);
            }
        });

        viewModel.getUnreadCount().observe(getViewLifecycleOwner(), count -> {
            if (count != null && count > 0) {
                badgeCount.setVisibility(View.VISIBLE);
                badgeCount.setText(String.valueOf(count));
            } else {
                badgeCount.setVisibility(View.GONE);
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), errorMsg -> {
            if (errorMsg != null && !errorMsg.isEmpty()) {
                Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getToastMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg != null && !msg.isEmpty()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                viewModel.clearToastMessage();
            }
        });
    }

    // ─── Add Member Dialog ───────────────────────────────────────

    /**
     * Shows the dialog for entering another user's shareable ID and optional request message.
     */
    private void showAddMemberDialog() {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_member);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }

        EditText inputId = dialog.findViewById(R.id.input_id);
        EditText inputMessage = dialog.findViewById(R.id.input_message);
        Button btnSendRequest = dialog.findViewById(R.id.btn_send_request);
        ImageButton btnClose = dialog.findViewById(R.id.btn_close);

        btnSendRequest.setOnClickListener(v -> {
            String id = inputId.getText().toString().trim();
            String message = inputMessage.getText().toString().trim();

            if (id.isEmpty()) {
                inputId.setError(getString(R.string.enter_member_id_error));
                return;
            }

            viewModel.sendFriendRequest(id, message);
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // ─── Notifications Dialog ────────────────────────────────────

    /**
     * Opens notification history, wires accept/reject/message actions, and marks notices viewed.
     */
    private void showNotificationsDialog() {
        // Mark requests as read when opening
        viewModel.markRequestsRead();

        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_friend_requests);

        ImageButton btnClose = dialog.findViewById(R.id.btn_close);
        Button btnClearHistory = dialog.findViewById(R.id.btn_clear_history);
        RecyclerView recyclerNotifications = dialog.findViewById(R.id.recycler_notifications);

        NotificationAdapter notificationAdapter = new NotificationAdapter(new NotificationAdapter.OnRequestActionListener() {
            @Override
            public void onAccept(FriendRequest request) {
                viewModel.acceptRequest(request);
            }

            @Override
            public void onIgnore(FriendRequest request) {
                viewModel.ignoreRequest(request);
            }

            @Override
            public void onCheckMessage(FriendRequest request) {
                showMessageDialog(request);
            }
        });

        recyclerNotifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerNotifications.setAdapter(notificationAdapter);

        notificationAdapter.setRequests(viewModel.getPendingRequests().getValue());
        Observer<List<FriendRequest>> observer = requests -> {
            if (requests != null) notificationAdapter.setRequests(requests);
        };
        viewModel.getPendingRequests().observe(getViewLifecycleOwner(), observer);

        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnClearHistory.setOnClickListener(v -> viewModel.clearNotificationHistory());
        dialog.setOnDismissListener(d -> viewModel.getPendingRequests().removeObserver(observer));

        dialog.show();
    }

    // ─── Message Dialog ──────────────────────────────────────────

    /**
     * Displays the optional text message attached to a friend request.
     */
    private void showMessageDialog(FriendRequest request) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_message);

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.9),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }

        TextView textTitle = dialog.findViewById(R.id.text_title);
        TextView textMessage = dialog.findViewById(R.id.text_message);
        ImageButton btnCloseIcon = dialog.findViewById(R.id.btn_close_icon);
        Button btnClose = dialog.findViewById(R.id.btn_close);

        String senderName = request.getFromName() != null
                ? request.getFromName()
                : getString(R.string.community_someone);
        textTitle.setText(getString(R.string.message_from_format, senderName));
        textMessage.setText(request.getMessage());

        btnCloseIcon.setOnClickListener(v -> dialog.dismiss());
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // ─── Member Detail Dialog ────────────────────────────────────

    /**
     * Shows a community member's adherence details, medications, nickname, and remove action.
     */
    private void showMemberDetailDialog(CommunityMember member) {
        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Material_Light_NoActionBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_member_detail);

        ImageButton btnClose = dialog.findViewById(R.id.btn_close);
        ImageButton btnEditTitle = dialog.findViewById(R.id.btn_edit_title);
        Button btnRemoveMember = dialog.findViewById(R.id.btn_remove_member);
        View nicknameRow = dialog.findViewById(R.id.nickname_row);
        TextView textCurrentNickname = dialog.findViewById(R.id.text_current_nickname);
        TextView textTitle = dialog.findViewById(R.id.text_title);
        TextView textSummaryTaken = dialog.findViewById(R.id.text_summary_taken);
        TextView textSummaryPending = dialog.findViewById(R.id.text_summary_pending);
        TextView textSummaryMissed = dialog.findViewById(R.id.text_summary_missed);
        TextView textEmpty = dialog.findViewById(R.id.text_empty);
        RecyclerView recyclerMedications = dialog.findViewById(R.id.recycler_medications);

        String displayName = member.getCustomTitle() != null && !member.getCustomTitle().trim().isEmpty()
                ? member.getCustomTitle().trim()
                : (member.getName() != null ? member.getName() : "");
        textTitle.setText(getString(R.string.member_medications, displayName));

        recyclerMedications.setLayoutManager(new LinearLayoutManager(requireContext()));

        btnClose.setOnClickListener(v -> dialog.dismiss());
        if (!member.isSelf()) {
            nicknameRow.setVisibility(View.VISIBLE);
            btnRemoveMember.setVisibility(View.VISIBLE);
            String nickname = member.getCustomTitle() != null && !member.getCustomTitle().trim().isEmpty()
                    ? member.getCustomTitle().trim()
                    : (member.getName() != null ? member.getName() : "");
            textCurrentNickname.setText(nickname);
            btnEditTitle.setOnClickListener(v -> showEditMemberTitleDialog(member, textCurrentNickname, textTitle));
            btnRemoveMember.setOnClickListener(v -> showRemoveMemberDialog(member, dialog));
        }

        final ListenerRegistration medicationListener = viewModel.observeMemberMedications(
                member.getUid(),
                new CommunityViewModel.MedicationListCallback() {
                    @Override
                    public void onResult(List<Map<String, Object>> medications, int taken, int pending, int missed) {
                        if (!isAdded()) return;

                        requireActivity().runOnUiThread(() -> {
                            textSummaryTaken.setText(getString(R.string.taken_count_format, taken));
                            textSummaryPending.setText(getString(R.string.pending_count_format, pending));
                            textSummaryMissed.setText(getString(R.string.missed_count_format, missed));

                            if (medications.isEmpty()) {
                                textEmpty.setVisibility(View.VISIBLE);
                                textEmpty.setText(R.string.no_medications_found);
                                recyclerMedications.setVisibility(View.GONE);
                            } else {
                                textEmpty.setVisibility(View.GONE);
                                recyclerMedications.setVisibility(View.VISIBLE);
                                recyclerMedications.setAdapter(new MedicationDetailAdapter(medications));
                            }
                        });
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded()) return;
                        requireActivity().runOnUiThread(() -> {
                            textEmpty.setVisibility(View.VISIBLE);
                            textEmpty.setText(message);
                            recyclerMedications.setVisibility(View.GONE);
                        });
                    }
                }
        );

        dialog.setOnDismissListener(d -> medicationListener.remove());
        dialog.show();
    }

    private void showRemoveMemberDialog(CommunityMember member, Dialog parentDialog) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirmation);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView textTitle = dialog.findViewById(R.id.text_title);
        TextView textMessage = dialog.findViewById(R.id.text_message);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnConfirm = dialog.findViewById(R.id.btn_confirm);

        textTitle.setText(R.string.remove_member);
        textMessage.setText(getString(R.string.remove_member_confirmation,
                member.getName() != null ? member.getName() : getString(R.string.member_label)));
        btnConfirm.setText(R.string.remove_member);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            viewModel.removeCommunityMember(member);
            dialog.dismiss();
            parentDialog.dismiss();
        });

        dialog.show();
    }

    private void showEditMemberTitleDialog(CommunityMember member, TextView textCurrentNickname, TextView textTitle) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_edit_info);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView dialogTitle = dialog.findViewById(R.id.text_title);
        TextView dialogSubtitle = dialog.findViewById(R.id.text_subtitle);
        EditText input = dialog.findViewById(R.id.input_value);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnSave = dialog.findViewById(R.id.btn_save);

        dialogTitle.setText(R.string.edit_member_title);
        dialogSubtitle.setText(R.string.edit_dialog_subtitle_nickname);
        input.setHint(R.string.edit_dialog_hint_nickname);
        input.setText(member.getCustomTitle() != null ? member.getCustomTitle() : "");
        input.setSelection(input.getText() != null ? input.getText().length() : 0);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String updatedTitle = input.getText() != null ? input.getText().toString().trim() : "";
            member.setCustomTitle(updatedTitle);
            String displayName = !updatedTitle.isEmpty()
                    ? updatedTitle
                    : (member.getName() != null ? member.getName() : "");
            textCurrentNickname.setText(displayName);
            textTitle.setText(getString(R.string.member_medications, displayName));
            viewModel.updateMemberCustomTitle(member.getUid(), updatedTitle);
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.refreshCommunityData();
    }

    private void animateCommunityState(View target) {
        target.setAlpha(0f);
        target.setTranslationY(20f);
        target.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .start();
    }

    // ─── Inner Adapter for Medication Detail ─────────────────────

    private class MedicationDetailAdapter extends RecyclerView.Adapter<MedicationDetailAdapter.MedViewHolder> {

        private final List<Map<String, Object>> medications;

        MedicationDetailAdapter(List<Map<String, Object>> medications) {
            this.medications = medications;
        }

        @Override
        public int getItemCount() {
            return medications.size();
        }

        @NonNull
        @Override
        public MedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_member_medication, parent, false);
            return new MedViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MedViewHolder holder, int position) {
            holder.bind(medications.get(position));
        }

        class MedViewHolder extends RecyclerView.ViewHolder {
            private final View statusDot;
            private final TextView textMedName;
            private final TextView textMedDetails;
            private final TextView textMedStatus;

            MedViewHolder(@NonNull View itemView) {
                super(itemView);
                statusDot = itemView.findViewById(R.id.status_dot);
                textMedName = itemView.findViewById(R.id.text_med_name);
                textMedDetails = itemView.findViewById(R.id.text_med_details);
                textMedStatus = itemView.findViewById(R.id.text_med_status);
            }

            void bind(Map<String, Object> med) {
                // Read new field names with fallback to old names
                String name = (String) med.get("medicationName");
                if (name == null) name = (String) med.get("name");
                String dosage = (String) med.get("dosage");
                String time = (String) med.get("scheduledTime");
                if (time == null) time = (String) med.get("time");
                String status = (String) med.get("status");
                String instructions = (String) med.get("instructions");
                String takenTime = (String) med.get("takenTime");

                // Name + dosage
                String nameText = name != null ? name : "";
                if (dosage != null && !dosage.isEmpty()) {
                    nameText += " " + dosage;
                }
                textMedName.setText(nameText);

                // Time + instructions
                String detailText = formatTime(time != null ? time : "");
                if (instructions != null && !instructions.isEmpty()) {
                    detailText += " - " + instructions;
                }
                textMedDetails.setText(detailText);

                // Status styling
                if ("TAKEN".equals(status)) {
                    String takenDisplay = formatTakenTime(takenTime);
                    if (takenDisplay != null) {
                        textMedStatus.setText("Taken at " + takenDisplay);
                    } else {
                        textMedStatus.setText(R.string.status_taken);
                    }
                    textMedStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.success));
                    statusDot.setBackgroundTintList(ContextCompat.getColorStateList(itemView.getContext(), R.color.success));
                } else if ("MISSED".equals(status)) {
                    textMedStatus.setText(R.string.status_missed);
                    textMedStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.error));
                    statusDot.setBackgroundTintList(ContextCompat.getColorStateList(itemView.getContext(), R.color.error));
                } else {
                    textMedStatus.setText(R.string.status_pending);
                    textMedStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.warning));
                    statusDot.setBackgroundTintList(ContextCompat.getColorStateList(itemView.getContext(), R.color.warning));
                }
            }

            /** Parse "yyyy-MM-dd HH:mm:ss" → "h:mm a" format, or return null */
            private String formatTakenTime(String takenDateTime) {
                if (takenDateTime == null || takenDateTime.isEmpty()) return null;
                try {
                    // Extract time portion from "yyyy-MM-dd HH:mm:ss"
                    String timePart = takenDateTime.contains(" ")
                            ? takenDateTime.split(" ")[1] : takenDateTime;
                    String[] parts = timePart.split(":");
                    int hour = Integer.parseInt(parts[0]);
                    int minute = Integer.parseInt(parts[1]);
                    String amPm = hour >= 12 ? "PM" : "AM";
                    int displayHour = hour % 12;
                    if (displayHour == 0) displayHour = 12;
                    return String.format("%d:%02d %s", displayHour, minute, amPm);
                } catch (Exception e) {
                    return null;
                }
            }

            private String formatTime(String time) {
                if (time == null || time.isEmpty()) return "";
                try {
                    String[] parts = time.split(":");
                    int hour = Integer.parseInt(parts[0]);
                    int minute = Integer.parseInt(parts[1]);
                    String amPm = hour >= 12 ? "PM" : "AM";
                    int displayHour = hour % 12;
                    if (displayHour == 0) displayHour = 12;
                    return String.format("%d:%02d %s", displayHour, minute, amPm);
                } catch (Exception e) {
                    return time;
                }
            }
        }
    }
}

package my.utar.uccd3223.medimind.presentation.community;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import my.utar.uccd3223.medimind.R;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private final List<FriendRequest> requests = new ArrayList<>();
    private final OnRequestActionListener listener;

    public interface OnRequestActionListener {
        void onAccept(FriendRequest request);
        void onIgnore(FriendRequest request);
        void onCheckMessage(FriendRequest request);
    }

    public NotificationAdapter(OnRequestActionListener listener) {
        this.listener = listener;
    }

    public void setRequests(List<FriendRequest> newRequests) {
        requests.clear();
        if (newRequests != null) {
            requests.addAll(newRequests);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(requests.get(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final View rootNotification;
        private final ImageView imgAvatar;
        private final TextView textSenderName;
        private final TextView textMessage;
        private final TextView textStatus;
        private final Button btnCheckMessage;
        private final LinearLayout actionButtons;
        private final Button btnAccept;
        private final Button btnIgnore;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            rootNotification = itemView.findViewById(R.id.root_notification);
            imgAvatar = itemView.findViewById(R.id.img_avatar);
            textSenderName = itemView.findViewById(R.id.text_sender_name);
            textMessage = itemView.findViewById(R.id.text_message);
            textStatus = itemView.findViewById(R.id.text_status);
            btnCheckMessage = itemView.findViewById(R.id.btn_check_message);
            actionButtons = itemView.findViewById(R.id.action_buttons);
            btnAccept = itemView.findViewById(R.id.btn_accept);
            btnIgnore = itemView.findViewById(R.id.btn_ignore);
        }

        void bind(FriendRequest request) {
            String senderName = request.getFromName() != null
                    ? request.getFromName()
                    : itemView.getContext().getString(R.string.community_someone);
            boolean pendingResponse = request.requiresResponse(request.getToUid());
            bindAvatar(request.getFromProfileImage());
            textSenderName.setText(senderName);
            textMessage.setText(buildMessage(request));
            if (pendingResponse) {
                textStatus.setVisibility(View.GONE);
            } else {
                textStatus.setVisibility(View.VISIBLE);
                textStatus.setText(buildStatus(request));
            }

            if (request.getMessage() != null && !request.getMessage().trim().isEmpty()) {
                btnCheckMessage.setVisibility(View.VISIBLE);
                btnCheckMessage.setOnClickListener(v -> {
                    if (listener != null) listener.onCheckMessage(request);
                });
            } else {
                btnCheckMessage.setVisibility(View.GONE);
            }

            if (pendingResponse) {
                actionButtons.setVisibility(View.VISIBLE);
                btnAccept.setOnClickListener(v -> {
                    if (listener != null) listener.onAccept(request);
                });
                btnIgnore.setOnClickListener(v -> {
                    if (listener != null) listener.onIgnore(request);
                });
                applyActiveState();
            } else {
                actionButtons.setVisibility(View.GONE);
                applyResolvedState();
            }
        }

        private String buildMessage(FriendRequest request) {
            android.content.Context context = itemView.getContext();
            String type = request.typeOrDefault();
            if ("member_removed".equals(type)) {
                return context.getString(R.string.notification_body_member_removed);
            }
            if ("accepted".equalsIgnoreCase(request.getStatus())) {
                return context.getString(R.string.notification_body_request_accepted);
            }
            if ("ignored".equalsIgnoreCase(request.getStatus())) {
                return context.getString(R.string.notification_body_request_ignored);
            }
            return context.getString(R.string.notification_body_request_pending);
        }

        private String buildStatus(FriendRequest request) {
            android.content.Context context = itemView.getContext();
            String type = request.typeOrDefault();
            if ("member_removed".equals(type)) {
                return context.getString(R.string.notification_status_removed_member);
            }
            if ("accepted".equalsIgnoreCase(request.getStatus())) {
                return context.getString(R.string.notification_status_accepted);
            }
            if ("ignored".equalsIgnoreCase(request.getStatus())) {
                return context.getString(R.string.notification_status_rejected);
            }
            return context.getString(R.string.notification_status_awaiting_response);
        }

        private void applyActiveState() {
            rootNotification.setAlpha(1f);
            textSenderName.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_primary));
            textMessage.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
            textStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.primary));
            btnCheckMessage.setEnabled(true);
        }

        private void applyResolvedState() {
            rootNotification.setAlpha(0.65f);
            textSenderName.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
            textMessage.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
            textStatus.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.text_secondary));
            btnCheckMessage.setEnabled(true);
        }

        private void bindAvatar(String imagePath) {
            if (imagePath != null && !imagePath.isEmpty()) {
                File imageFile = new File(imagePath);
                if (imageFile.exists()) {
                    imgAvatar.setPadding(0, 0, 0, 0);
                    imgAvatar.setImageTintList(null);
                    Glide.with(itemView.getContext())
                            .load(imageFile)
                            .apply(RequestOptions.circleCropTransform()
                                    .skipMemoryCache(true)
                                    .diskCacheStrategy(DiskCacheStrategy.NONE))
                            .into(imgAvatar);
                    return;
                }
            }
            Glide.with(itemView.getContext()).clear(imgAvatar);
            int padding = (int) (12 * itemView.getResources().getDisplayMetrics().density);
            imgAvatar.setPadding(padding, padding, padding, padding);
            imgAvatar.setImageResource(R.drawable.ic_person);
            imgAvatar.setImageTintList(ContextCompat.getColorStateList(itemView.getContext(), R.color.on_primary));
        }
    }
}

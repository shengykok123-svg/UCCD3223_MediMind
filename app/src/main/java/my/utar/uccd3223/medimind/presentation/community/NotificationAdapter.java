package my.utar.uccd3223.medimind.presentation.community;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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
        private final TextView textMessage;
        private final Button btnCheckMessage;
        private final LinearLayout actionButtons;
        private final Button btnAccept;
        private final Button btnIgnore;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textMessage = itemView.findViewById(R.id.text_message);
            btnCheckMessage = itemView.findViewById(R.id.btn_check_message);
            actionButtons = itemView.findViewById(R.id.action_buttons);
            btnAccept = itemView.findViewById(R.id.btn_accept);
            btnIgnore = itemView.findViewById(R.id.btn_ignore);
        }

        void bind(FriendRequest request) {
            // Message text
            String senderName = request.getFromName() != null ? request.getFromName() : "Someone";
            textMessage.setText(itemView.getContext().getString(R.string.new_friend_request, senderName));

            // Show check message button if message exists
            if (request.getMessage() != null && !request.getMessage().trim().isEmpty()) {
                btnCheckMessage.setVisibility(View.VISIBLE);
                btnCheckMessage.setOnClickListener(v -> {
                    if (listener != null) listener.onCheckMessage(request);
                });
            } else {
                btnCheckMessage.setVisibility(View.GONE);
            }

            // Show accept/ignore for pending requests
            if ("pending".equals(request.getStatus())) {
                actionButtons.setVisibility(View.VISIBLE);
                btnAccept.setOnClickListener(v -> {
                    if (listener != null) listener.onAccept(request);
                });
                btnIgnore.setOnClickListener(v -> {
                    if (listener != null) listener.onIgnore(request);
                });
            } else {
                actionButtons.setVisibility(View.GONE);
            }
        }
    }
}

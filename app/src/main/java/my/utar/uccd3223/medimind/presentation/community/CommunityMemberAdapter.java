package my.utar.uccd3223.medimind.presentation.community;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

public class CommunityMemberAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_MEMBER = 0;
    private static final int VIEW_TYPE_ADD = 1;

    private final List<CommunityMember> members = new ArrayList<>();
    private final List<CommunityMember> allMembers = new ArrayList<>();
    private final OnMemberClickListener listener;

    public interface OnMemberClickListener {
        void onMemberClick(CommunityMember member);
        void onAddMemberClick();
    }

    public CommunityMemberAdapter(OnMemberClickListener listener) {
        this.listener = listener;
    }

    public void setMembers(List<CommunityMember> newMembers) {
        allMembers.clear();
        if (newMembers != null) {
            allMembers.addAll(newMembers);
        }
        members.clear();
        members.addAll(allMembers);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        members.clear();
        if (query == null || query.trim().isEmpty()) {
            members.addAll(allMembers);
        } else {
            String lowerQuery = query.toLowerCase().trim();
            for (CommunityMember member : allMembers) {
                if (member.getName() != null
                        && member.getName().toLowerCase().contains(lowerQuery)) {
                    members.add(member);
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (position == members.size()) {
            return VIEW_TYPE_ADD;
        }
        return VIEW_TYPE_MEMBER;
    }

    @Override
    public int getItemCount() {
        return members.size() + 1; // +1 for add card
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_ADD) {
            View view = inflater.inflate(R.layout.item_add_member_card, parent, false);
            return new AddViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_community_member, parent, false);
            return new MemberViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MemberViewHolder) {
            ((MemberViewHolder) holder).bind(members.get(position));
        } else if (holder instanceof AddViewHolder) {
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) listener.onAddMemberClick();
            });
        }
    }

    class MemberViewHolder extends RecyclerView.ViewHolder {
        private final ImageView imgAvatar;
        private final TextView textRelation;
        private final TextView textName;
        private final TextView textTakenCount;
        private final TextView textPendingCount;
        private final TextView textMissedCount;

        MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            imgAvatar = itemView.findViewById(R.id.img_avatar);
            textRelation = itemView.findViewById(R.id.text_relation);
            textName = itemView.findViewById(R.id.text_name);
            textTakenCount = itemView.findViewById(R.id.text_taken_count);
            textPendingCount = itemView.findViewById(R.id.text_pending_count);
            textMissedCount = itemView.findViewById(R.id.text_missed_count);
        }

        void bind(CommunityMember member) {
            // Relation label
            if (member.isSelf()) {
                textRelation.setText(R.string.me_label);
            } else {
                textRelation.setText(member.getName() != null ? member.getName() : "");
            }

            // Name
            textName.setText(member.getName() != null ? member.getName() : "");

            // Avatar — use Glide with circle crop, matching SettingsFragment pattern
            String imagePath = member.getProfileImagePath();
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
                } else {
                    setDefaultAvatar();
                }
            } else {
                setDefaultAvatar();
            }

            // Adherence counts
            textTakenCount.setText(itemView.getContext().getString(R.string.taken_count_format, member.getTakenCount()));
            textPendingCount.setText(itemView.getContext().getString(R.string.pending_count_format, member.getPendingCount()));
            textMissedCount.setText(itemView.getContext().getString(R.string.missed_count_format, member.getMissedCount()));

            // Click listener
            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onMemberClick(member);
            });
        }

        private void setDefaultAvatar() {
            Glide.with(itemView.getContext()).clear(imgAvatar);
            int padding = (int) (16 * itemView.getResources().getDisplayMetrics().density);
            imgAvatar.setPadding(padding, padding, padding, padding);
            imgAvatar.setImageResource(R.drawable.ic_person);
            imgAvatar.setImageTintList(ContextCompat.getColorStateList(itemView.getContext(), R.color.on_primary));
        }
    }

    static class AddViewHolder extends RecyclerView.ViewHolder {
        AddViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}

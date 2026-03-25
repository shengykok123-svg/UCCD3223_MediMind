package my.utar.uccd3223.medimind.presentation.ai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import my.utar.uccd3223.medimind.databinding.ItemChatMessageAiBinding;
import my.utar.uccd3223.medimind.databinding.ItemChatMessageUserBinding;

public class ChatMessageAdapter extends ListAdapter<ChatMessage, RecyclerView.ViewHolder> {

    private static final DiffUtil.ItemCallback<ChatMessage> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ChatMessage>() {
                @Override
                public boolean areItemsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull ChatMessage oldItem, @NonNull ChatMessage newItem) {
                    return oldItem.equals(newItem);
                }
            };

    public ChatMessageAdapter() {
        super(DIFF_CALLBACK);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == ChatMessage.TYPE_USER) {
            ItemChatMessageUserBinding binding = ItemChatMessageUserBinding.inflate(inflater, parent, false);
            return new UserViewHolder(binding);
        } else {
            // TYPE_AI and TYPE_LOADING both use AI bubble layout
            ItemChatMessageAiBinding binding = ItemChatMessageAiBinding.inflate(inflater, parent, false);
            return new AiViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = getItem(position);
        if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).bind(message);
        } else if (holder instanceof AiViewHolder) {
            ((AiViewHolder) holder).bind(message);
        }
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatMessageUserBinding binding;

        UserViewHolder(ItemChatMessageUserBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ChatMessage message) {
            binding.textMessage.setText(message.getText());

            if (message.getImageBitmap() != null) {
                binding.imageAttachment.setVisibility(View.VISIBLE);
                binding.imageAttachment.setImageBitmap(message.getImageBitmap());
            } else {
                binding.imageAttachment.setVisibility(View.GONE);
            }
        }
    }

    static class AiViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatMessageAiBinding binding;

        AiViewHolder(ItemChatMessageAiBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ChatMessage message) {
            binding.textMessage.setText(message.getText());
        }
    }
}

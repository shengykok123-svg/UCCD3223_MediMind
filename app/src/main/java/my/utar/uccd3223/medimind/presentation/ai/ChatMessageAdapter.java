package my.utar.uccd3223.medimind.presentation.ai;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BulletSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import my.utar.uccd3223.medimind.databinding.ItemChatMessageAiBinding;
import my.utar.uccd3223.medimind.databinding.ItemChatMessageUserBinding;

/**
 * Renders user/AI chat bubbles and formats lightweight Markdown returned by the API.
 */
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

    /**
     * Uses the message type as the RecyclerView view type so user and AI bubbles
     * can be inflated from different layouts.
     */
    @Override
    public int getItemViewType(int position) {
        return getItem(position).getType();
    }

    /**
     * Creates the correct bubble view holder for user messages or AI/loading messages.
     */
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

    /**
     * Binds each chat item to the matching holder implementation.
     */
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

        /**
         * Shows the user's text and optional image attachment.
         */
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

        /**
         * Shows AI/loading text after applying lightweight Markdown formatting.
         */
        void bind(ChatMessage message) {
            binding.textMessage.setText(formatMarkdown(message.getText()));
        }
    }

    /**
     * Converts common Markdown markers from the API into Android spans.
     * This keeps bold headings and bullets readable inside a normal TextView.
     */
    private static CharSequence formatMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        SpannableStringBuilder output = new SpannableStringBuilder();
        String normalized = normalizeMarkdownLines(text);
        int index = 0;
        while (index < normalized.length()) {
            int start = normalized.indexOf("**", index);
            if (start < 0) {
                output.append(normalized.substring(index));
                break;
            }

            int end = normalized.indexOf("**", start + 2);
            if (end < 0) {
                output.append(normalized.substring(index));
                break;
            }

            output.append(normalized.substring(index, start));
            int boldStart = output.length();
            output.append(normalized.substring(start + 2, end));
            output.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    boldStart,
                    output.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            index = end + 2;
        }
        applyBulletSpans(output);
        return output;
    }

    /**
     * Pre-processes Markdown line syntax before spans are applied.
     * Headings become bold text, bullet markers become bullet characters,
     * numbered lists are normalized, and code-fence markers are hidden.
     */
    private static String normalizeMarkdownLines(String text) {
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        StringBuilder normalized = new StringBuilder();
        boolean inCodeBlock = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }

            if (!inCodeBlock) {
                if (trimmed.matches("#{1,6}\\s+.+")) {
                    line = trimmed.replaceFirst("#{1,6}\\s+", "**") + "**";
                } else if (trimmed.matches("[-*]\\s+.+")) {
                    line = "\u2022 " + trimmed.substring(2).trim();
                } else if (trimmed.matches("\\d+[.)]\\s+.+")) {
                    line = trimmed.replaceFirst("^(\\d+)[.)]\\s+", "$1. ");
                }
            }

            normalized.append(line);
            if (i < lines.length - 1) {
                normalized.append('\n');
            }
        }
        return normalized.toString().trim();
    }

    /**
     * Replaces temporary bullet characters with BulletSpan indentation.
     */
    private static void applyBulletSpans(SpannableStringBuilder text) {
        int lineStart = 0;
        while (lineStart < text.length()) {
            int lineEnd = nextLineEnd(text, lineStart);
            if (lineEnd > lineStart + 1
                    && text.charAt(lineStart) == '\u2022'
                    && text.charAt(lineStart + 1) == ' ') {
                text.delete(lineStart, lineStart + 2);
                lineEnd -= 2;
                text.setSpan(
                        new BulletSpan(24),
                        lineStart,
                        Math.max(lineStart, lineEnd),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
            lineStart = lineEnd + 1;
        }
    }

    /**
     * Finds the end index of the current line for span range calculations.
     */
    private static int nextLineEnd(CharSequence text, int start) {
        int index = start;
        while (index < text.length() && text.charAt(index) != '\n') {
            index++;
        }
        return index;
    }
}

package my.utar.uccd3223.medimind.presentation.ai;

import android.graphics.Bitmap;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable chat item model for user, AI, loading, and optional image messages.
 */
public class ChatMessage {

    public static final int TYPE_USER = 0;
    public static final int TYPE_AI = 1;
    public static final int TYPE_LOADING = 2;

    private final String id;
    private final String text;
    private final int type;
    private final long timestamp;
    private final Bitmap imageBitmap;

    public ChatMessage(String text, int type) {
        this(text, type, null);
    }

    public ChatMessage(String text, int type, Bitmap imageBitmap) {
        this.id = UUID.randomUUID().toString();
        this.text = text;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.imageBitmap = imageBitmap;
    }

    public String getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public int getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Bitmap getImageBitmap() {
        return imageBitmap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatMessage that = (ChatMessage) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

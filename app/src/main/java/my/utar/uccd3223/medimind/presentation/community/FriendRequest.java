package my.utar.uccd3223.medimind.presentation.community;

public class FriendRequest {
    private String id;
    private String fromUid;
    private String toUid;
    private String fromName;
    private String toName;
    private String message;
    private String status; // "pending", "accepted", "ignored"
    private long timestamp;
    private boolean read;

    public FriendRequest() {}

    public FriendRequest(String id, String fromUid, String toUid, String fromName,
                         String toName, String message, String status, long timestamp, boolean read) {
        this.id = id;
        this.fromUid = fromUid;
        this.toUid = toUid;
        this.fromName = fromName;
        this.toName = toName;
        this.message = message;
        this.status = status;
        this.timestamp = timestamp;
        this.read = read;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFromUid() { return fromUid; }
    public void setFromUid(String fromUid) { this.fromUid = fromUid; }

    public String getToUid() { return toUid; }
    public void setToUid(String toUid) { this.toUid = toUid; }

    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }

    public String getToName() { return toName; }
    public void setToName(String toName) { this.toName = toName; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}

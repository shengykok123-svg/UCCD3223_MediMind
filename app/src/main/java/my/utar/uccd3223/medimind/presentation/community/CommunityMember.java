package my.utar.uccd3223.medimind.presentation.community;

public class CommunityMember {
    private String uid;
    private String name;
    private String customTitle;
    private String shareableId;
    private String profileImagePath;
    private int takenCount;
    private int missedCount;
    private int pendingCount;
    private int totalCount;
    private boolean isSelf;

    public CommunityMember() {}

    public CommunityMember(String uid, String name, String shareableId, boolean isSelf) {
        this.uid = uid;
        this.name = name;
        this.shareableId = shareableId;
        this.isSelf = isSelf;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCustomTitle() { return customTitle; }
    public void setCustomTitle(String customTitle) { this.customTitle = customTitle; }

    public String getShareableId() { return shareableId; }
    public void setShareableId(String shareableId) { this.shareableId = shareableId; }

    public String getProfileImagePath() { return profileImagePath; }
    public void setProfileImagePath(String profileImagePath) { this.profileImagePath = profileImagePath; }

    public int getTakenCount() { return takenCount; }
    public void setTakenCount(int takenCount) { this.takenCount = takenCount; }

    public int getMissedCount() { return missedCount; }
    public void setMissedCount(int missedCount) { this.missedCount = missedCount; }

    public int getPendingCount() { return pendingCount; }
    public void setPendingCount(int pendingCount) { this.pendingCount = pendingCount; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public boolean isSelf() { return isSelf; }
    public void setSelf(boolean self) { isSelf = self; }
}

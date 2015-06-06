package kornell.core.to;

import kornell.core.entity.ChatThreadType;


public interface UnreadChatThreadTO {
	public static final String TYPE = TOFactory.PREFIX+"unreadChatThread+json";

	String getUnreadMessages();
	void setUnreadMessages(String unreadMessages);

	String getChatThreadUUID();
	void setChatThreadUUID(String chatThreadUUID);

//	String getChatThreadName();
//	void setChatThreadName(String chatThreadName);

	String getChatThreadCreatorName();
	void setChatThreadCreatorName(String chatThreadCreatorName);
	
	String getCourseClassUUID();
	void setCourseClassUUID(String courseClassUUID);
	
	String getCourseClassName();
	void setCourseClassName(String courseClassName);
	
	ChatThreadType getThreadType();
	void setThreadType(ChatThreadType threadType);
}
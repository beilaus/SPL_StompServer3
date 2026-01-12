package bgu.spl.net.impl.data;

import java.util.concurrent.ConcurrentHashMap;

public class User {
	public final String name;
	public final String password;
	private int connectionId;
	private boolean isLoggedIn = false;
	private ConcurrentHashMap<String, Integer> userSubs = new ConcurrentHashMap<>();

	public User(int connectionId, String name, String password) {
		this.connectionId = connectionId;
		this.name = name;
		this.password = password;
	}

	public boolean isLoggedIn() {
		return isLoggedIn;
	}

	public void login() {
		isLoggedIn = true;
	}

	public void logout() {
		isLoggedIn = false;
	}

	public int getConnectionId() {
		return connectionId;
	}

	public void setConnectionId(int connectionId) {
		this.connectionId = connectionId;
	}
	
	public ConcurrentHashMap<String, Integer> getUserSubs() {
		return userSubs;
	}
	public void addSub(String topic, int subId) {
		userSubs.put(topic, subId);
	}


}

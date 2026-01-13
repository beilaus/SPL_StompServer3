package bgu.spl.net.impl.data;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.LinkedList;

public class User {
	public final String name;
	public final String password;
	private int connectionId;
	private boolean isLoggedIn = false;
	private CopyOnWriteArrayList<Subscriber> userSubs = new CopyOnWriteArrayList<>();
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

	public void subscribe(Subscriber sub) {
		userSubs.add(sub);
	}

	public void unSubscribe(int subId) {
		userSubs.removeIf(sub -> sub.getSubId() == subId);
	}
	public LinkedList<String> unSubscribeAll(){
		LinkedList<String> topics = new LinkedList<>();
		for(Subscriber sub : userSubs){
			topics.add(sub.getTopic());
		}
		userSubs = new CopyOnWriteArrayList<>();
		return topics;
	}

	public boolean subIdExists(int subId){
		for(Subscriber sub : userSubs){
			if(sub.getSubId() == subId){
				return true;
			}
		}
		return false;
	}

	public boolean alreadySubscribed(String topic){
		for(Subscriber sub : userSubs){
			if(sub.getTopic().equals(topic)){
				return true;
			}
		}
		return false;
	}

	public Subscriber getSub(int subId){
		for(Subscriber sub : userSubs){
			if(sub.getSubId() == subId){
				return sub;
			}
		}
		return null; //Shouldn't happen
	}


}

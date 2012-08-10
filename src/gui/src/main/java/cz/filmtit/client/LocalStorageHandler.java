package cz.filmtit.client;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.core.client.Scheduler.ScheduledCommand;
import com.google.gwt.storage.client.Storage;
import com.google.gwt.user.client.Window;

import cz.filmtit.client.callables.SetUserTranslation;
import cz.filmtit.client.dialogs.GoingOfflineDialog;

public class LocalStorageHandler {

    // TODO: a bunch of Dialogs instead of the Alerts and Confirms.
	
	// TODO: use a special object stored in the Storage as a descriptor of the data in the storage
	// probably under the userID as the key
	// (but beware because the user can modify the contents of the Storage)

	private static Storage storage = Storage.getLocalStorageIfSupported();
	
	private static boolean isStorageSupported = Storage.isLocalStorageSupported();
	
    public static boolean isStorageSupported() {
		return isStorageSupported;
	}

	/**
	 * Number of yet unfinished calls.
	 */
	private static int yetToUpload;
	
	public static void decrementYetToUpload () {
		yetToUpload--;
		if (yetToUpload == 0) {
			uploading = false;
			Gui.log("All requests from local storage returned!");
			Gui.getPageHandler().refresh();
			if (failedCount == 0) {
				Window.alert("All " + succeededCount + " items stored from Offline Mode " +
				"have been successfully saved!");				
			}
			else {
				StringBuilder sb = new StringBuilder();
				sb.append(succeededCount);
				sb.append(" items stored from Offline Mode have been successfully saved! However, ");
				sb.append(failedCount);
				sb.append(" items could not be stored. Error message from the server: ");
				for (Object error : failedMessages.keySet().toArray()) {
					sb.append('\n');
					sb.append(error);
				}
				Window.alert(sb.toString());
			}
		}
	}
	
	private static int succeededCount;
	public static void SuccessOnLoadFromLocalStorage (Storable object) {
		removeFromLocalStorage(object);
		succeededCount++;
		decrementYetToUpload();
	}
	
	private static int failedCount;
	private static List<Storable> failedObjects;
	private static Map<String, Integer> failedMessages;
	public static void FailureOnLoadFromLocalStorage (Storable object, String errorMessage) {
		failedCount++;
		failedObjects.add(object);
		Integer count = failedMessages.get(errorMessage);
		if (count != null) {
			failedMessages.put(errorMessage, count+1);
		}
		else {
			failedMessages.put(errorMessage, 1);			
		}
		decrementYetToUpload();
	}
	
	/**
	 * Whether the user is in online mode (default) or offline mode (experimental now).
	 */
	private static boolean online = true;
	
	public static boolean isOnline() {
		return online;
	}

	/**
	 * Whether the user is now uploading data from offline mode.
	 */
	private static boolean uploading = false;
	
	public static boolean isUploading() {
		return uploading;
	}

	/**
	 * Whether GoingOfflineDialog is currently open.
	 */
	private static boolean offeringOfflineStorage = false;
	
	public static boolean isOfferingOfflineStorage() {
		return offeringOfflineStorage;
	}
	
	/**
	 * Whether GoingOfflineDialog has been shown to the user.
	 */
	private static boolean offeredOfflineStorage = false;
	
	/**
	 * Temporary queue of calls that failed because of the problems with connection to server.
	 */
	public static List<Storable> queue;

	/**
	 * Switch the online/offline mode.
	 * When going offline, the Offline Mode is initialized
	 * and all Storables are saved into Local Storage instead of sending them to the server.
	 * When going online, Local Storage is inspected for saved Storables,
	 * and if some are found, the user is offered to upload them.
	 * @param online
	 */
	public static void setOnline(boolean online) {
		LocalStorageHandler.online = online;
		
		if (online) {
			// going online
			final int itemsNo = numberOfItemsInLocalStorage();
			if (itemsNo > 0) {
				Gui.log("Inspecting " + itemsNo + " items from local storage...");
				List<KeyValuePair> objects = loadUserObjectsFromLocalStorage();
				int count = (objects == null ? 0 : objects.size());
				Gui.log("Found " + count + " items from local storage.");
											
				if (count > 0) {
					boolean loadItems = Window.confirm(
							"There are " + count + " items stored in your browser " +
							"from the Offline Mode. " +
							"Do you want to upload them to the server now?");
					if (loadItems) {
						uploadUserObjects(objects);
					}
				}
			}
		}
		else {
			// going offline
			offeringOfflineStorage = false;
			offeredOfflineStorage = true;
			if (Storage.isLocalStorageSupported()) {
				Gui.guiStructure.offline_mode();
				for (Storable storableInError : queue) {
					storeInLocalStorage(storableInError);
				}
				queue = null;
			}
			else {
				Window.alert("Unfortunately, Offline Mode is not supported for your browser. " +
						"You need a browser that supports the Storage API. " +
						"(Most of the new versions of browsers support this.)");
				online = false;
			}
		}
	}
	
	public static void offerOfflineStorage(Storable storableInError) {
		if (queue == null) {
			queue = new LinkedList<Storable>();
		}
		queue.add(storableInError);
		if (Storage.isLocalStorageSupported() && !offeredOfflineStorage) {
			offeringOfflineStorage = true;
			new GoingOfflineDialog();
		}
		else {
			Window.alert("There is no connection to the server. " +
					"It is not possible to continue with the translation at the moment. " +
					"All translations you have done when online are safely stored on the server " +
					"(except for the last one or two, including '" + storableInError.toUserFriendlyString() + "') " +
					"but please do not fill in any new ones now as they would be lost. " +
					"Please try again later when the connection to the server is available again.");
		}
	}
	
	/**
     * 
     * @return Number of items found in local storage (can be 0).
     */
	public static int numberOfItemsInLocalStorage() {
    	if (isStorageSupported) {
    		return storage.getLength();
    	}
    	else {
    		return 0;
    	}
	}

	/**
	 * a separator of username at the end of the key
	 */
	public static final String USERNAME_SEPARATOR = "@";
	
	/**
	 * a separator of class at the end of the key but before the username
	 */
	public static final String CLASSID_SEPARATOR = ":";
	
	/**
	 * A separator of the individual fields in the value,
	 * and in the key
	 * after class and username are stripped off
	 */
	public static final String FIELDS_SEPARATOR = ";";
	
	/**
	 * Loads all objects
	 * belonging to the current user
	 * from the local storage.
	 */
	private static List<KeyValuePair> loadUserObjectsFromLocalStorage() {
		if (isStorageSupported) {
			List<KeyValuePair> objects = new LinkedList<KeyValuePair>();
			List<KeyValuePair> corrupted = new LinkedList<KeyValuePair>();
			// go through all items
			for (int i = 0; i < storage.getLength(); i++) {
				String key = storage.key(i);
				Gui.log("Found item with key " + key);
				String[] keyFields = key.split(USERNAME_SEPARATOR, 2);
				// check the key
				if (keyFields.length == 2) {
					String key_without_username = keyFields[0];
					String username = keyFields[1];
					// check username
					if (username.equals(Gui.getUsername())) {
						String value = storage.getItem(key);
						objects.add(new KeyValuePair(key_without_username, value));
					}
					// else somebody elses object, just keep it
				}
				else {
					corrupted.add(new KeyValuePair(key, storage.getItem(key)));
				}
			}
			for (KeyValuePair keyValuePair : corrupted) {
				Gui.log("Removing corrupted item " + keyValuePair);
				storage.removeItem(keyValuePair.getKey());
			}
			return objects;
		}
		else {
			return null;
		}
	}
	
	private static void uploadUserObjects(final List<KeyValuePair> keyValuePairs) {
		
		uploading = true;
		yetToUpload = keyValuePairs.size();
		succeededCount = 0;
		failedCount = 0;
		failedObjects = new LinkedList<Storable>();	
		failedMessages = new HashMap<String, Integer>();
		
		Scheduler.get().scheduleDeferred(new ScheduledCommand() {
			@Override
			public void execute() {
				// convert the objects
				List<Storable> objects = convertToStorable(keyValuePairs);
				// upload the objects
				// TODO RepeatingCommand
				for (Storable object : objects) {
					object.onLoadFromLocalStorage();
				}
				Gui.log("Sent " + objects.size() + " requests.");
			}
		});
		
	}
	
	/**
	 * Loads all objects
	 * belonging to the current user
	 * from the local storage.
	 * The key must contain the class_id
	 * and not contain the username.
	 */
	private static List<Storable> convertToStorable(List<KeyValuePair> keyValuePairs) {
		List<Storable> objects = new LinkedList<Storable>();
		// go through all items
		for (KeyValuePair keyValuePair : keyValuePairs) {
			Gui.log("Creating item " + keyValuePair);
			Storable object = loadObject(keyValuePair);
			// check that object was successfully created
			if (object != null) {
				objects.add(object);
			}
			else {
				Gui.log("Removing corrupted item " + keyValuePair);
				storage.removeItem(keyValuePair.getKey() + USERNAME_SEPARATOR + Gui.getUsername());
			}
		}
		return objects;
	}
	
	/**
	 * Determines the class of the object and loads it.
	 * The key must contain the class_id
	 * and not contain the username.
	 * @return The object on success, null otherwise.
	 */
	private static Storable loadObject(KeyValuePair keyValuePair) {
		Storable object = null;
		
		String[] keyFields = keyValuePair.getKey().split(CLASSID_SEPARATOR, 2);
		// check the key
		if (keyFields.length == 2) {
	    	String key = keyFields[0];
	    	String classId = keyFields[1];
	    	// class switch
	    	if (classId.equals(SetUserTranslation.CLASS_ID)) {
				object = SetUserTranslation.fromKeyValuePair(new KeyValuePair(key, keyValuePair.getValue()));
			}
			// else if other class...
		}

		return object;
	}
	
	/**
	 * Stores the object into the local storage.
	 * @return true if the object was successfully saved, false if not
	 */
    public static boolean storeInLocalStorage (Storable storable) {
    	if (isStorageSupported) {
    		KeyValuePair keyValuePair = storable.toKeyValuePair();
    		String key = fullKey(keyValuePair.getKey(), storable.getClassID());
    		String value = keyValuePair.getValue();
    		storage.setItem(key, value);
    		// check
    		if (storage.getItem(key).equals(value)) {
    			return true;
    		}
    		else {
    			return false;
    		}
		}
    	else {
			return false;
		}
	}
	
	/**
	 * Removes the object from the local storage.
	 */
    public static void removeFromLocalStorage (Storable storable) {
    	if (Storage.isLocalStorageSupported()) {
    		KeyValuePair keyValuePair = storable.toKeyValuePair();
    		String key = fullKey(keyValuePair.getKey(), storable.getClassID());
    		storage.removeItem(key);
		}
	}
    
    /**
     * Add class id and username to the key.
     * @param key
     * @return
     */
    private static String fullKey (String key, String classId) {
		StringBuilder keyBuilder = new StringBuilder();
		keyBuilder.append(key);
		keyBuilder.append(CLASSID_SEPARATOR);
		keyBuilder.append(classId);
		keyBuilder.append(USERNAME_SEPARATOR);
		keyBuilder.append(Gui.getUsername());
		return keyBuilder.toString();
    }

}

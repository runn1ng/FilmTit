package cz.filmtit.client.callables;

import cz.filmtit.client.Gui;
import cz.filmtit.client.pages.Settings;

/**
 * Change user's password.
 */
public class SetPassword extends SetSetting<String> {

    /**
     * Change user's password.
     * Does <b>not</b> enqueue the call immediately,
     * call enqueue() explicitly!
     */
	public SetPassword(String setting, Settings settingsPage) {
		super(setting, settingsPage);
	}
	
	@Override
	protected void call() {
		filmTitService.setPassword(Gui.getSessionID(), setting, this);
	}

}


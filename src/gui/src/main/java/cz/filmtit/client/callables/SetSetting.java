package cz.filmtit.client.callables;

import cz.filmtit.client.Callable;
import cz.filmtit.client.pages.Settings;

/**
 * An ancestor to methods setting some settings.
 */
public abstract class SetSetting<T> extends Callable<Void> {
	
	// parameters
	protected T setting;
	private Settings settingsPage;

	@Override
	public String getName() {
		return getNameWithParameters(setting);
	}
	
    @Override
    public void onSuccessAfterLog(Void o) {
    	settingsPage.success();
    }

    @Override
    protected void onFinalError(String message) {
        settingsPage.error(message);
    }
    
    // constructor
    /**
     * Does <b>not</b> enqueue the call immediately,
     * call enqueue() explicitely!
     */
    public SetSetting(T setting, Settings settingsPage) {
		super();
		
		this.setting = setting;
		this.settingsPage = settingsPage;

		// do not enqueue on construction
        // enqueue();
	}

}

package kornell.gui.client.presentation.util;

import kornell.gui.client.KornellConstants;

import com.google.gwt.core.client.GWT;

public class EnumTranslator {

	private static KornellConstants constants = GWT.create(KornellConstants.class);
	
	public static String translateEnum(Enum<?> e) {
	    return constants.getString(e.getClass().getSimpleName() + '_' + e.name());
	  }
}

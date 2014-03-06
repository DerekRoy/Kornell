package kornell.gui.client.sequence;

import kornell.api.client.KornellClient;
import kornell.api.client.KornellSession;
import kornell.gui.client.presentation.course.ClassroomPlace;

import com.google.gwt.core.shared.GWT;
import com.google.gwt.place.shared.PlaceController;
import com.google.web.bindery.event.shared.EventBus;

public class SequencerFactoryImpl implements SequencerFactory {

	private KornellSession session;
	private EventBus bus;
	private PlaceController ctrl;
	

	public SequencerFactoryImpl(EventBus bus, PlaceController ctrl,
			KornellSession session) {
		this.session = session;
		this.bus = bus;
		this.ctrl = ctrl;
	}

	@Override
	public Sequencer withPlace(ClassroomPlace place) {
		GWT.log("Creating course sequencer");		
		Sequencer sequencer = null;
		sequencer = new PrefetchSequencer(bus,session);
		return sequencer.withPlace(place);
	}

}
package kornell.gui.client.sequence;

import kornell.api.client.KornellClient;
import kornell.api.client.UserSession;
import kornell.gui.client.presentation.course.CoursePlace;

import com.google.gwt.place.shared.PlaceController;
import com.google.web.bindery.event.shared.EventBus;

public class SequencerFactoryImpl implements SequencerFactory {

	private KornellClient client;
	private EventBus bus;
	private PlaceController ctrl;
	private Sequencer sequencer;

	public SequencerFactoryImpl(EventBus bus, PlaceController ctrl,
			KornellClient client) {
		this.client = client;
		this.bus = bus;
		this.ctrl = ctrl;
	}

	@Override
	public Sequencer withPlace(CoursePlace place) {
		if (sequencer == null)
			sequencer = new CourseSequencer(bus,client);
		return sequencer.withPlace(place);
	}

}

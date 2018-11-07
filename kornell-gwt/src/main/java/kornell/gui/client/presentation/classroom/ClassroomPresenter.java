package kornell.gui.client.presentation.classroom;

import com.github.gwtbootstrap.client.ui.Alert;
import com.github.gwtbootstrap.client.ui.constants.AlertType;
import com.google.gwt.core.client.GWT;
import com.google.gwt.place.shared.PlaceChangeEvent;
import com.google.gwt.place.shared.PlaceController;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import kornell.api.client.Callback;
import kornell.api.client.KornellSession;
import kornell.core.entity.ContentSpec;
import kornell.core.entity.Course;
import kornell.core.entity.CourseClass;
import kornell.core.entity.Enrollment;
import kornell.core.entity.EnrollmentState;
import kornell.core.entity.EnrollmentsEntries;
import kornell.core.entity.EntityState;
import kornell.core.error.KornellErrorTO;
import kornell.core.lom.Contents;
import kornell.core.to.CourseClassTO;
import kornell.core.to.EnrollmentLaunchTO;
import kornell.gui.client.GenericClientFactoryImpl;
import kornell.gui.client.KornellConstants;
import kornell.gui.client.event.HideSouthBarEvent;
import kornell.gui.client.event.ShowPacifierEvent;
import kornell.gui.client.personnel.classroom.WizardTeacher;
import kornell.gui.client.presentation.vitrine.VitrinePlace;
import kornell.gui.client.sequence.Sequencer;
import kornell.gui.client.sequence.SequencerFactory;
import kornell.gui.client.util.ClientProperties;
import kornell.gui.client.util.forms.FormHelper;
import kornell.gui.client.util.view.KornellNotification;
import kornell.scorm.client.scorm12.SCORM12Runtime;

public class ClassroomPresenter implements ClassroomView.Presenter {

    private static KornellConstants constants = GWT.create(KornellConstants.class);
    private static final String PREFIX = ClientProperties.PREFIX + "Classroom";

    private ClassroomView view;
    private ClassroomPlace place;
    private PlaceController placeCtrl;
    private SequencerFactory sequencerFactory;
    private KornellSession session;
    private Contents contents;
    private Sequencer sequencer;
    private EventBus bus;
    private SCORM12Runtime runtime;
    private boolean showCertificationTab;

    public ClassroomPresenter(EventBus bus, ClassroomView view, PlaceController placeCtrl, SequencerFactory seqFactory,
            KornellSession session) {
        this.bus = bus;
        this.view = view;
        view.setPresenter(this);
        this.placeCtrl = placeCtrl;
        this.sequencerFactory = seqFactory;
        this.session = session;

        bus.addHandler(PlaceChangeEvent.TYPE, new PlaceChangeEvent.Handler() {
            @Override
            public void onPlaceChange(PlaceChangeEvent event) {
                stopSequencer();
                if(!(event.getNewPlace() instanceof ClassroomPlace) && runtime != null){
                    runtime.stop();
                }
            }
        });
    }

    private void displayPlace() {
        if (session.isAnonymous()) {
            placeCtrl.goTo(new VitrinePlace());
            return;
        }

        view.asWidget().setVisible(false);

        bus.fireEvent(new ShowPacifierEvent(true));
        session.courseClasses().getByEnrollment(place.getEnrollmentUUID(), new Callback<CourseClassTO>() {
            @Override
            public void ok(CourseClassTO courseClassTO) {
                bus.fireEvent(new ShowPacifierEvent(false));
                courseClassFetched(courseClassTO);
            }

            @Override
            public void notFound(KornellErrorTO kornellErrorTO) {
                bus.fireEvent(new ShowPacifierEvent(false));
                courseClassFetched(null);
            }
        });
    }

    private void courseClassFetched(CourseClassTO courseClassTO) {

        Enrollment enrollment = courseClassTO != null ? courseClassTO.getEnrollment() : null;
        if (enrollment == null) {
            session.setCurrentCourseClass((CourseClassTO) null);
        } else {
            ClientProperties.set(
                    PREFIX + ClientProperties.SEPARATOR + session.getCurrentUser().getPerson().getUUID()
                    + ClientProperties.SEPARATOR + ClientProperties.CURRENT_ENROLLMENT,
                    courseClassTO.getEnrollment().getUUID());

            session.setCurrentCourseClass(courseClassTO);
        }

        courseClassTO = session.getCurrentCourseClass();
        CourseClass courseClass = courseClassTO != null ? courseClassTO.getCourseClass() : null;
        Course course = courseClassTO != null ? courseClassTO.getCourseVersionTO().getCourseTO().getCourse() : null;
        EntityState courseClassState = courseClass != null ? courseClass.getState() : null;

        final boolean isEnrolled = enrollment != null && EnrollmentState.enrolled.equals(enrollment.getState());
        final boolean isExpired = enrollment != null && courseClass != null && FormHelper.isEnrollmentExpired(courseClass, enrollment);
        final boolean isCourseClassActive = courseClassState != null && !EntityState.inactive.equals(courseClassState);
        final boolean isWizardCourse = course != null && ContentSpec.WIZARD.equals(course.getContentSpec());
        final boolean isClassroomJsonNeededAndAbscent = isWizardCourse && new WizardTeacher(courseClassTO).getClassroomJson() == null;
        final boolean showCourseClassContent = enrollment == null
                || (isEnrolled && isCourseClassActive && !isClassroomJsonNeededAndAbscent && !isExpired);

        if (showCourseClassContent) {
            bus.fireEvent(new ShowPacifierEvent(true));
            final Alert alert = KornellNotification.show(constants.loadingTheCourse(), AlertType.WARNING, -1);
            bus.addHandler(PlaceChangeEvent.TYPE, new PlaceChangeEvent.Handler() {
                @Override
                public void onPlaceChange(PlaceChangeEvent event) {
                    alert.close();
                }
            });
            session.enrollment(place.getEnrollmentUUID()).launch(new Callback<EnrollmentLaunchTO>() {

                @Override
                public void ok(EnrollmentLaunchTO to) {
                    alert.close();
                    loadRuntime(to.getEnrollmentEntries());
                    loadContents(place.getEnrollmentUUID(), to.getContents(), to.isShowCertificationTab());
                };

                private void loadRuntime(EnrollmentsEntries enrollmentEntries) {
                    runtime = SCORM12Runtime.launch(bus, session, placeCtrl, enrollmentEntries);
                }

                private void loadContents(final String enrollmentUUID, final Contents contents, boolean showCertificationTab) {
                    // check if user has a valid enrollment to this course
                    boolean shouldHideSouthBar = (session.getCurrentCourseClass().getCourseVersionTO()
                            .getCourseVersion().getParentVersionUUID() == null);
                    GenericClientFactoryImpl.EVENT_BUS.fireEvent(new HideSouthBarEvent(shouldHideSouthBar));
                    setContents(contents);
                    setShowCertificationTab(showCertificationTab);
                    view.display(showCourseClassContent);
                    view.asWidget().setVisible(true);
                    bus.fireEvent(new ShowPacifierEvent(false));
                }

            });
        } else {
            GenericClientFactoryImpl.EVENT_BUS.fireEvent(new HideSouthBarEvent());
            setContents(null);
            view.display(showCourseClassContent);
            view.asWidget().setVisible(true);
        }
    }

    private FlowPanel getPanel() {
        return view.getContentPanel();
    }

    @Override
    public void startSequencer() {
        if (contents != null) {
            sequencer = sequencerFactory.withPlace(place).withPanel(getPanel());
            sequencer.go(contents);
        }
    }

    @Override
    public void stopSequencer() {
        if (sequencer != null) {
            sequencer.stop();
        }
    }

    @Override
    public Contents getContents() {
        return contents;
    }

    private void setContents(Contents contents) {
        this.contents = contents;
    }

    @Override
    public boolean getShowCertificationTab() {
        return showCertificationTab;
    }

    private void setShowCertificationTab(boolean showCertificationTab) {
        this.showCertificationTab = showCertificationTab;
    }

    @Override
    public Widget asWidget() {
        return view.asWidget();
    }

    public void setPlace(ClassroomPlace place) {
        this.place = place;
        displayPlace();
    }

    @Override
    public void fireProgressEvent() {
        if (sequencer != null) {
            sequencer.fireProgressEvent();
        }
    }
}

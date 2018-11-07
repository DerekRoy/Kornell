package kornell.gui.client.presentation.classroom;

import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.IsWidget;

import kornell.core.lom.Contents;

public interface ClassroomView extends IsWidget {
    public interface Presenter extends IsWidget {
        Contents getContents();

        boolean getShowCertificationTab();

        void startSequencer();

        void stopSequencer();

        void fireProgressEvent();
    }

    FlowPanel getContentPanel();

    void setPresenter(Presenter presenter);

    void display(boolean showCourseClassContent);

}

package kornell.core.to;

import kornell.core.entity.EnrollmentsEntries;
import kornell.core.lom.Contents;

public interface EnrollmentLaunchTO {
    public static final String TYPE = TOFactory.PREFIX + "enrollmentlaunchto+json";

    Contents getContents();
    void setContents(Contents contents);

    EnrollmentsEntries getEnrollmentEntries();
    void setEnrollmentEntries(EnrollmentsEntries entries);

    boolean isShowCertificationTab();
    void setShowCertificationTab(boolean showCertificationTab);

}

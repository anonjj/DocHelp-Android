package com.example.dochelp.session;

import com.example.dochelp.R;

public class AppointmentSession {
    private static final AppointmentSession I = new AppointmentSession();
    public static AppointmentSession get() { return I; }

    public String apptId;
    public String patientId;
    public String doctorId;

    public void set(String apptId, String patientId, String doctorId) {
        this.apptId = apptId;
        this.patientId = patientId;
        this.doctorId = doctorId;
    }

    public void clear() { apptId = patientId = doctorId = null; }
}

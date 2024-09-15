package com.udasecurity.service;

import com.udasecurity.application.StatusListener;
import com.udasecurity.data.AlarmStatus;
import com.udasecurity.data.ArmingStatus;
import com.udasecurity.data.SecurityRepository;
import com.udasecurity.data.Sensor;
import com.udasecurity.service.image.FakeImageService;

import java.awt.image.BufferedImage;
import java.util.*;
import java.util.function.Consumer;

/**
 * Service that receives information about changes to the security system. Responsible for
 * forwarding updates to the repository and making any decisions about changing the system state.
 *
 * This is the class that should contain most of the business logic for our system, and it is the
 * class you will be writing unit tests for.
 */
public class SecurityService {

    private FakeImageService imageService;
    private SecurityRepository securityRepository;
    private Set<StatusListener> statusListeners = new HashSet<>();
    // Section 4: Fix Any Bugs You Find With Your Unit Tests!
    private static Boolean flagDetectionStatus = false;

    public SecurityService(SecurityRepository securityRepository, FakeImageService imageService) {
        this.securityRepository = securityRepository;
        this.imageService = imageService;
    }

    /**
     * Sets the current arming status for the system. Changing the arming status
     * may update both the alarm status.
     *
     * @param armingStatus
     */
    public void setArmingStatus(ArmingStatus armingStatus) {
        Runnable updateAlarmStatus = determineAlarmStatusUpdate(armingStatus);

        updateAlarmStatus.run();

        securityRepository.setArmingStatus(armingStatus);

        statusListeners.forEach(StatusListener::sensorStatusChanged);
    }

    private Runnable determineAlarmStatusUpdate(ArmingStatus armingStatus) {
        if (armingStatus == ArmingStatus.ARMED_HOME && flagDetectionStatus) {
            return () -> setAlarmStatus(AlarmStatus.ALARM);
        } else if (armingStatus == ArmingStatus.DISARMED) {
            return () -> setAlarmStatus(AlarmStatus.NO_ALARM);
        } else {
            return () -> getSensors().forEach(sensor -> changeSensorActivationStatus(sensor, false));
        }
    }

    /**
     * Internal method that handles alarm status changes based on whether
     * the camera currently shows a cat.
     *
     * @param cat True if a cat is detected, otherwise false.
     */
    private void catDetected(Boolean cat) {

        flagDetectionStatus = cat;

        Runnable updateAlarmStatus = () -> setAlarmStatus(cat && getArmingStatus() == ArmingStatus.ARMED_HOME
                ? AlarmStatus.ALARM
                : getSensors().stream().anyMatch(Sensor::getActive)
                ? getAlarmStatus()
                : AlarmStatus.NO_ALARM);

        updateAlarmStatus.run();

        statusListeners.forEach(sl -> sl.catDetected(cat));
    }

    /**
     * Register the StatusListener for alarm system updates from within the SecurityService.
     *
     * @param statusListener
     */
    public void addStatusListener(StatusListener statusListener) {
        statusListeners.add(statusListener);
    }

    public void removeStatusListener(StatusListener statusListener) {
        statusListeners.remove(statusListener);
    }

    /**
     * Change the alarm status of the system and notify all listeners.
     *
     * @param status
     */
    public void setAlarmStatus(AlarmStatus status) {
        securityRepository.setAlarmStatus(status);
        statusListeners.forEach(sl -> sl.notify(status));
    }

    /**
     * Internal method for updating the alarm status when a sensor has been activated.
     */
    private void handleSensorActivated() {
        if (securityRepository.getArmingStatus() == ArmingStatus.DISARMED) {
            return; //no problem if the system is disarmed
        }
        switch (securityRepository.getAlarmStatus()) {
            case NO_ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.ALARM);
        }
    }

    /**
     * Internal method for updating the alarm status when a sensor has been deactivated
     */
    void handleSensorDeactivated() {
        switch (securityRepository.getAlarmStatus()) {
            case PENDING_ALARM -> setAlarmStatus(AlarmStatus.NO_ALARM);
            case ALARM -> setAlarmStatus(AlarmStatus.PENDING_ALARM);
        }
    }

    /**
     * Change the activation status for the specified sensor and update alarm status if necessary.
     *
     * @param sensor
     * @param active
     */
    public void changeSensorActivationStatus(Sensor sensor, Boolean active) {

        EnumMap<AlarmStatus, Consumer<Sensor>> statusHandlers = new EnumMap<>(AlarmStatus.class);

        statusHandlers.put(AlarmStatus.NO_ALARM, s -> handleSensorUpdate(s, active));
        statusHandlers.put(AlarmStatus.PENDING_ALARM, s -> handleSensorUpdate(s, active));

        // Xử lý mặc định nếu trạng thái không phải NO_ALARM hoặc PENDING_ALARM
        statusHandlers.getOrDefault(securityRepository.getAlarmStatus(), s -> {}).accept(sensor);

        sensor.setActive(active);
        securityRepository.updateSensor(sensor);
    }

    private void handleSensorUpdate(Sensor sensor, Boolean isActive) {
        boolean isSensorCurrentlyActive = sensor.getActive();

        if ((isSensorCurrentlyActive && isActive) || (!isSensorCurrentlyActive && isActive)) {
            handleSensorActivated();
        } else if (isSensorCurrentlyActive && !isActive) {
            handleSensorDeactivated();
        }
    }


    /**
     * Send an image to the SecurityService for processing. The securityService will use its provided
     * ImageService to analyze the image for cats and update the alarm status accordingly.
     *
     * @param currentCameraImage
     */
    public void processImage(BufferedImage currentCameraImage) {
        catDetected(imageService.imageContainsCat(currentCameraImage, 50.0f));
    }

    public AlarmStatus getAlarmStatus() {
        return securityRepository.getAlarmStatus();
    }

    public Set<Sensor> getSensors() {
        return securityRepository.getSensors();
    }

    public void addSensor(Sensor sensor) {
        securityRepository.addSensor(sensor);
    }

    public void removeSensor(Sensor sensor) {
        securityRepository.removeSensor(sensor);
    }

    public ArmingStatus getArmingStatus() {
        return securityRepository.getArmingStatus();
    }
}

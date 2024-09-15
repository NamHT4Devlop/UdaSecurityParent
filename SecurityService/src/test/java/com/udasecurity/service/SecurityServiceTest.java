package com.udasecurity.service;

import com.udasecurity.application.StatusListener;
import com.udasecurity.data.*;
import com.udasecurity.service.image.FakeImageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {
    @InjectMocks
    private SecurityService securityServiceMockTest;

    @Spy // Using @Spy to create a partial mock, allowing real method invocations
    private SecurityRepository securityRepositorySpy = Mockito.mock(SecurityRepository.class); // Using mock for SecurityRepository

    @Spy // Using @Spy to allow for method stubbing and real method calls
    private FakeImageService fakeImageServiceSpy = Mockito.mock(FakeImageService.class); // Using mock for FakeImageService

    @Spy // StatusListener spy for real and mocked behaviors
    private StatusListener statusListener = Mockito.mock(StatusListener.class); // Using mock for StatusListener

    @BeforeEach
    void init() {
        securityServiceMockTest = new SecurityService(securityRepositorySpy, fakeImageServiceSpy);
    }

    @CsvSource({
            "Test_Case_1_Arm_System_Activate_Sensor_Pending_Alarm_Status", // Test case for armed system activating a sensor
            "Test_Case_2_Arm_System_Activate_Sensor_in_Pending_Alarm_Transition_to_Alarm_Status", // Test case for transitioning to alarm
            "Test_Case_3_Pending_Alarm_All_Sensors_Inactive_Return_to_No_Alarm", // Test case for all sensors inactive
            "Test_Case_4_Alarm_Active_Sensor_State_Changes_Should_Not_Affect_Alarm", // Test case for state changes
            "Test_Case_5_Pending_Alarm_Sensor_Already_Active_Transition_to_Alarm_Status", // Test case for already active sensor
            "Test_Case_6_Deactivate_Inactive_Sensor_No_Change_in_Alarm_State", // Test case for deactivating inactive sensor
            "Test_Case_7_Disarmed_System_Should_Not_Activate_Alarm", // Test case for disarmed state
            "Test_Case_8_Alarm_No_Change_When_Sensor_Deactivated" // Test case for deactivating a sensor when alarm is already in alarm state
    })
    @ParameterizedTest(name = "name_test_case_{0}")
    void updateSensorState_OnActivationOrDeactivation(String testName) {
        Sensor receivedInput = new Sensor("Sensor", SensorType.DOOR);
        setupSecurityRepositoryMocks(testName, receivedInput);
        try {
            executeChangeSensorActivationStatus(testName, receivedInput);
            verifyAlarmStatus(testName);
        } catch (Exception ex) {
            throw ex;
        }
    }

    private void setupSecurityRepositoryMocks(String testName, Sensor input) {
        Map<String, Runnable> mockSetup = new HashMap<>();

        // Set up common mock behavior
        mockSetup.put("Test_Case_1_Arm_System_Activate_Sensor_Pending_Alarm_Status", () -> setupAlarm(ArmingStatus.ARMED_HOME, AlarmStatus.NO_ALARM));
        mockSetup.put("Test_Case_2_Arm_System_Activate_Sensor_in_Pending_Alarm_Transition_to_Alarm_Status", () -> setupAlarm(ArmingStatus.ARMED_HOME, AlarmStatus.PENDING_ALARM));
        mockSetup.put("Test_Case_3_Pending_Alarm_All_Sensors_Inactive_Return_to_No_Alarm", () -> {
            input.setActive(false);
            setupAlarm(ArmingStatus.ARMED_HOME, AlarmStatus.PENDING_ALARM);
        });
        mockSetup.put("Test_Case_4_Alarm_Active_Sensor_State_Changes_Should_Not_Affect_Alarm", () -> setAlarmStatus(AlarmStatus.ALARM));
        mockSetup.put("Test_Case_5_Pending_Alarm_Sensor_Already_Active_Transition_to_Alarm_Status", () -> {
            input.setActive(true);
            setAlarmStatus(AlarmStatus.PENDING_ALARM);
        });
        mockSetup.put("Test_Case_6_Deactivate_Inactive_Sensor_No_Change_in_Alarm_State", () -> {
            input.setActive(false);
            setAlarmStatus(AlarmStatus.PENDING_ALARM);
        });

        // 7. Test for disarmed system where activating a sensor should not change the alarm state
        mockSetup.put("Test_Case_7_Disarmed_System_Should_Not_Activate_Alarm", () -> setupAlarm(ArmingStatus.DISARMED, AlarmStatus.NO_ALARM));

        // 8. Test for deactivating a sensor when the alarm is already active
        mockSetup.put("Test_Case_8_Alarm_No_Change_When_Sensor_Deactivated", () -> {
            input.setActive(false); // Deactivate sensor
            setAlarmStatus(AlarmStatus.ALARM); // Alarm is already active
        });

        // Execute the corresponding mock setup based on the test case identifier
        mockSetup.getOrDefault(testName, () -> {}).run();
    }

    // Helper method to set the arming and alarm status
    private void setupAlarm(ArmingStatus armingStatus, AlarmStatus alarmStatus) {
        setArmingStatus(armingStatus);
        setAlarmStatus(alarmStatus);
    }

    /**
     * Sets the arming status in the security repository mock.
     */
    private void setArmingStatus(ArmingStatus status) {
        Mockito.doReturn(status).when(securityRepositorySpy).getArmingStatus();
    }

    /**
     * Sets the alarm status in the security repository mock.
     */
    private void setAlarmStatus(AlarmStatus status) {
        Mockito.doReturn(status).when(securityRepositorySpy).getAlarmStatus();
    }

    private void executeChangeSensorActivationStatus(String testName, Sensor input) {
        Map<String, Runnable> executionLogic = getStringRunnableMap(input);

        // Default case: if the testName doesn't match any specific case, activate the sensor
        executionLogic.putIfAbsent(testName, () -> {
            securityServiceMockTest.changeSensorActivationStatus(input, true);
        });

        // Execute the logic for the given test case, or default if testName is not found
        Runnable action = executionLogic.get(testName);
        if (action != null) {
            action.run();
        } else {
            throw new IllegalArgumentException("Unknown test case: " + testName);
        }
    }

    private Map<String, Runnable> getStringRunnableMap(Sensor input) {
        Map<String, Runnable> executionLogic = new HashMap<>();

        // Define execution logic for when the alarm is pending and all sensors are inactive
        executionLogic.put("Test_Case_3_Pending_Alarm_All_Sensors_Inactive_Return_to_No_Alarm", () -> {
            // Activate the sensor and then deactivate it
            securityServiceMockTest.changeSensorActivationStatus(input, true);
            securityServiceMockTest.changeSensorActivationStatus(input, false);
        });

        // Define execution logic for when the alarm status is pending, sensors are inactive
        executionLogic.put("Test_Case_8_Alarm_No_Change_When_Sensor_Deactivated", () -> {
            // Deactivate the sensor without changing the alarm state
            securityServiceMockTest.changeSensorActivationStatus(input, false);
        });

        return executionLogic;
    }

    private void verifyAlarmStatus(String testName) {
        Map<String, Runnable> verificationLogic = new HashMap<>();

        // Define verification logic for various scenarios
        verificationLogic.put("Test_Case_1_Arm_System_Activate_Sensor_Pending_Alarm_Status", () -> verifyAlarmStatusSet(AlarmStatus.PENDING_ALARM));
        verificationLogic.put("Test_Case_2_Arm_System_Activate_Sensor_in_Pending_Alarm_Transition_to_Alarm_Status", () -> verifyAlarmStatusSet(AlarmStatus.ALARM));
        verificationLogic.put("Test_Case_3_Pending_Alarm_All_Sensors_Inactive_Return_to_No_Alarm", () -> verifyAlarmStatusSet(AlarmStatus.NO_ALARM));
        verificationLogic.put("Test_Case_4_Alarm_Active_Sensor_State_Changes_Should_Not_Affect_Alarm", () -> verifyAlarmStatusNotChanged());
        verificationLogic.put("Test_Case_5_Pending_Alarm_Sensor_Already_Active_Transition_to_Alarm_Status", () -> verifyAlarmStatusSet(AlarmStatus.ALARM));
        verificationLogic.put("Test_Case_6_Deactivate_Inactive_Sensor_No_Change_in_Alarm_State", () -> verifyAlarmStatusNotChanged());

        // New verifications for additional test cases
        verificationLogic.put("Test_Case_7_Disarmed_System_Should_Not_Activate_Alarm", () -> verifyAlarmStatusNotChanged()); // Verify no change when disarmed
        verificationLogic.put("Test_Case_8_Alarm_No_Change_When_Sensor_Deactivated", () -> verifyAlarmStatusNotChanged()); // Verify no change when deactivating in alarm

        // Execute the corresponding verification logic based on the testName
        Runnable verificationAction = verificationLogic.getOrDefault(testName, () -> {
            throw new IllegalArgumentException("Unknown test case: " + testName);
        });
        verificationAction.run();
    }

    // Helper method to verify that the alarm status was set to a specific value
    private void verifyAlarmStatusSet(AlarmStatus expectedStatus) {
        verify(securityRepositorySpy).setAlarmStatus(expectedStatus);
    }

    // Helper method to verify that the alarm status was not set to NO_ALARM or PENDING_ALARM
    private void verifyAlarmStatusNotChanged() {
        verify(securityRepositorySpy, Mockito.never()).setAlarmStatus(AlarmStatus.NO_ALARM);
        verify(securityRepositorySpy, Mockito.never()).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }


    @ParameterizedTest(name = "name_test_case_{0}")
    @CsvSource({
            "Test_Case_7_Armed-Home_Cat_Detected_in_Camera_Trigger_Alarm",
            "Test_Case_8_Armed_Home_No_Cat_in_Camera_Return_to_No_Alarm_(If_Sensors_Inactive)",
            "Test_Case_11_Armed-Home_Cat_Detected_Set_Alarm_Status_to_Alarm",
            "Test_Case_12_Armed_Home_No_Cat_with_Active_Sensor_No_Alarm_Status_Change"
    })
    void analyzeImageForDetection(String testName) {
        setupProcessImageMocks(testName);
        BufferedImage sampleBufferedImage = Mockito.mock(BufferedImage.class);

        // Execute the method under test
        securityServiceMockTest.processImage(sampleBufferedImage);

        // Verify the behavior of the method based on the test case
        verifyProcessImage(testName);
    }

    private void setupProcessImageMocks(String testName) {
        Map<String, Runnable> mockSetup = new HashMap<>();

        // Define mock setup for when an image containing a cat is detected and the system is armed
        mockSetup.put("Test_Case_7_Armed-Home_Cat_Detected_in_Camera_Trigger_Alarm", () -> {
            Mockito.when(fakeImageServiceSpy.imageContainsCat(Mockito.any(), Mockito.anyFloat())).thenReturn(true);
            Mockito.when(securityRepositorySpy.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        });

        // Define mock setup for when no cat is detected and sensors are inactive
        mockSetup.put("Test_Case_8_Armed_Home_No_Cat_in_Camera_Return_to_No_Alarm_(If_Sensors_Inactive)", () -> {
            Sensor sensor = new Sensor("Sensor", SensorType.DOOR);
            sensor.setActive(false); // Sensor is inactive
            Mockito.when(fakeImageServiceSpy.imageContainsCat(Mockito.any(), Mockito.anyFloat())).thenReturn(false);
            // Return a list of sensors as a set
            Mockito.when(securityServiceMockTest.getSensors()).thenReturn(new HashSet<>(Collections.singletonList(sensor)));
        });

        // Define mock setup for when the system is armed home and a cat is detected
        mockSetup.put("Test_Case_11_Armed-Home_Cat_Detected_Set_Alarm_Status_to_Alarm", () -> {
            Mockito.when(fakeImageServiceSpy.imageContainsCat(Mockito.any(), Mockito.anyFloat())).thenReturn(true);
            Mockito.when(securityRepositorySpy.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        });

        // Define mock setup for when no cat is detected but at least one sensor is active
        mockSetup.put("Test_Case_12_Armed_Home_No_Cat_with_Active_Sensor_No_Alarm_Status_Change", () -> {
            Sensor sensor = new Sensor("ActiveSensor", SensorType.DOOR);
            sensor.setActive(true); // Sensor is active
            Mockito.when(fakeImageServiceSpy.imageContainsCat(Mockito.any(), Mockito.anyFloat())).thenReturn(false);
            // Return a list of sensors as a set
            Mockito.when(securityServiceMockTest.getSensors()).thenReturn(new HashSet<>(Collections.singletonList(sensor)));
        });

        // Execute the corresponding mock setup logic based on the test case name
        mockSetup.getOrDefault(testName, () -> {
            // Default action when no matching test case is found
        }).run();
    }

    private void verifyProcessImage(String testName) {
        Map<String, Runnable> verificationLogic = new HashMap<>();

        // Define verification logic for when an image containing a cat is detected and the system is armed
        verificationLogic.put("Test_Case_7_Armed-Home_Cat_Detected_in_Camera_Trigger_Alarm", () -> {
            verify(securityRepositorySpy).setAlarmStatus(AlarmStatus.ALARM);
        });

        // Define verification logic for when no cat is detected and sensors are inactive
        verificationLogic.put("Test_Case_8_Armed_Home_No_Cat_in_Camera_Return_to_No_Alarm_(If_Sensors_Inactive)", () -> {
            verify(securityRepositorySpy).setAlarmStatus(AlarmStatus.NO_ALARM);
        });

        // Define verification logic for when the system is armed home and a cat is detected
        verificationLogic.put("Test_Case_11_Armed-Home_Cat_Detected_Set_Alarm_Status_to_Alarm", () -> {
            verify(securityRepositorySpy).setAlarmStatus(AlarmStatus.ALARM);
        });

        // Define verification logic for when no cat is detected but at least one sensor is active
        verificationLogic.put("Test_Case_12_Armed_Home_No_Cat_with_Active_Sensor_No_Alarm_Status_Change", () -> {
            verify(securityRepositorySpy, Mockito.never()).setAlarmStatus(AlarmStatus.NO_ALARM);
        });

        // Execute the corresponding verification logic based on the test case name
        verificationLogic.getOrDefault(testName, () -> {
            System.out.println("No matching verification logic for test case: " + testName);
        }).run();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "Test_Case_9_Disarm_System_Set_Status_to_No_Alarm",
            "Test_Case_13_resetSensors_WhenSystemArmedAway_SensorsBecomeInactive",
            "Test_Case_10_Arm_System_Reset_All_Sensors_to_Inactive"
    })
    void updateSystemArmingState(String testName) {
        Set<Sensor> sensorSet = setupSetArmingStatusMocks(testName);
        try {
            executeSetArmingStatus(testName);
            // Verify that the behavior is correct based on the test case
            verifySetArmingStatus(testName, sensorSet);
        } catch (Exception ex) {
            // Re-throw the exception if one occurs
            throw ex;
        }
    }

    private Set<Sensor> setupSetArmingStatusMocks(String testName) {
        Set<Sensor> sensorSet = new HashSet<>();
        Map<String, Runnable> mockSetup = new HashMap<>();

        // Add mock behaviors for both Armed Away and Armed At Home cases
        mockSetup.put("Test_Case_13_resetSensors_WhenSystemArmedAway_SensorsBecomeInactive", () -> setupMocks(sensorSet));
        mockSetup.put("Test_Case_10_Arm_System_Reset_All_Sensors_to_Inactive", () -> setupMocks(sensorSet));
        // Execute the mock setup for the given test case
        mockSetup.getOrDefault(testName, () -> {}).run();

        // Return the set of sensors configured for the test
        return sensorSet;
    }

    // Helper method to set up mocks
    private void setupMocks(Set<Sensor> sensorSet) {
        sensorSet.add(new Sensor("FrontDoorSensor", SensorType.DOOR));
        sensorSet.add(new Sensor("LivingRoomWindowSensor", SensorType.WINDOW));
        sensorSet.add(new Sensor("LivingRoomMotionSensor", SensorType.MOTION));

        // Mock the behavior of the securityRepository to return specific statuses and sensors
        Mockito.doReturn(AlarmStatus.PENDING_ALARM).when(securityRepositorySpy).getAlarmStatus();
        Mockito.doReturn(sensorSet).when(securityRepositorySpy).getSensors();

        // Set all sensors to active initially
        sensorSet.forEach(sensor -> sensor.setActive(true));
    }


    private void executeSetArmingStatus(String testName) {
        Map<String, ArmingStatus> statusMap = new HashMap<>();

        // Populate the map with test cases and their corresponding arming statuses
        //9.If the system is disarmed, set the status to no alarm.
        statusMap.put("Test_Case_9_Disarm_System_Set_Status_to_No_Alarm", ArmingStatus.DISARMED);
        statusMap.put("Test_Case_13_resetSensors_WhenSystemArmedAway_SensorsBecomeInactive", ArmingStatus.ARMED_AWAY);
        statusMap.put("Test_Case_10_Arm_System_Reset_All_Sensors_to_Inactive", ArmingStatus.ARMED_HOME);

        // Retrieve the corresponding ArmingStatus for the test case and set it, or do nothing if not found
        ArmingStatus status = statusMap.get(testName);
        if (status != null) {
            securityServiceMockTest.setArmingStatus(status);
        }
    }


    private void verifySetArmingStatus(String testName, Set<Sensor> sensorSet) {
        Map<String, Runnable> verificationLogic = new HashMap<>();

        Runnable verifySensorsInactive = () -> {
            for (Sensor sensor : sensorSet) {
                // Check if each sensor is inactive
                assertFalse(sensor.getActive(), "Sensor should be inactive: " + sensor.getName());
            }
        };

        // Define verification logic for when the system is disarmed
        verificationLogic.put("Test_Case_9_Disarm_System_Set_Status_to_No_Alarm", () -> {
            // Verify that the alarm status is set to NO_ALARM when the system is disarmed
            verify(securityRepositorySpy).setAlarmStatus(AlarmStatus.NO_ALARM);
        });

        // Define verification logic for when the system is armed away
        verificationLogic.put("Test_Case_13_resetSensors_WhenSystemArmedAway_SensorsBecomeInactive", verifySensorsInactive);

        // Define verification logic for when the system is armed at home
        //If the system is armed, reset all sensors to inactive.
        verificationLogic.put("Test_Case_10_Arm_System_Reset_All_Sensors_to_Inactive", verifySensorsInactive);

        // Execute the corresponding verification logic based on the test case identifier
        // If the testName does not match any key in the map, execute a no-op lambda
        verificationLogic.getOrDefault(testName, () -> {
            // Default case when no matching test case is found
            System.out.println("No matching verification logic for test case: " + testName);
        }).run();
    }

    // Enums for action types to improve type safety and readability
    enum ActionType {
        ADD_STATUS_LISTENER,
        REMOVE_STATUS_LISTENER,
        ADD_SENSOR,
        REMOVE_SENSOR
    }

    // Consolidated test for status listener actions
    @Test
    void testSecurityServiceActionsAndAlarmStatus() {
        Sensor sensor = new Sensor("SensorTest", SensorType.DOOR);

        // Use thenAnswer to dynamically return alarm status based on input
        Mockito.when(securityRepositorySpy.getAlarmStatus()).thenAnswer(invocation -> {
            AlarmStatus currentStatus = AlarmStatus.ALARM;
            // Change logic here if needed
            return currentStatus;
        });

        // Testing status listener actions
        performAction(ActionType.ADD_STATUS_LISTENER, null, statusListener);
        performAction(ActionType.REMOVE_STATUS_LISTENER, null, statusListener);

        // Testing sensor actions
        performAction(ActionType.ADD_SENSOR, sensor, null);
        performAction(ActionType.REMOVE_SENSOR, sensor, null);

        // Execute method that should change the alarm status
        securityServiceMockTest.handleSensorDeactivated();

        // Verify that the alarm status was set to PENDING_ALARM
        verify(securityRepositorySpy).setAlarmStatus(AlarmStatus.PENDING_ALARM);

        // Simulate returning the new expected alarm status
        Mockito.when(securityRepositorySpy.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        // Verify getAlarmStatus method
        AlarmStatus expectedStatus = AlarmStatus.PENDING_ALARM;
        AlarmStatus actualStatus = securityServiceMockTest.getAlarmStatus();

        // Verify the returned alarm status
        assertEquals(expectedStatus, actualStatus, "The returned alarm status should match the expected status.");
    }

    private void performAction(ActionType actionType, Sensor sensor, StatusListener statusListener) {
        Map<ActionType, Runnable> actions = new HashMap<>();

        // If the actionType is ADD_STATUS_LISTENER, add the statusListener to the securityService.
        actions.put(ActionType.ADD_STATUS_LISTENER, () -> securityServiceMockTest.addStatusListener(statusListener));

        // If the actionType is REMOVE_STATUS_LISTENER, remove the statusListener from the securityService.
        actions.put(ActionType.REMOVE_STATUS_LISTENER, () -> securityServiceMockTest.removeStatusListener(statusListener));

        // If the actionType is ADD_SENSOR, add the sensor to the securityService.
        actions.put(ActionType.ADD_SENSOR, () -> securityServiceMockTest.addSensor(sensor));

        // If the actionType is REMOVE_SENSOR, remove the sensor from the securityService.
        actions.put(ActionType.REMOVE_SENSOR, () -> securityServiceMockTest.removeSensor(sensor));

        // Retrieve the corresponding action (Runnable) for the given actionType.
        // This is where we dynamically decide which action to perform based on the actionType.
        Runnable action = actions.get(actionType);

        // Check if the action for the provided actionType exists in the map.
        // If an action is found (action != null), run it using action.run().
        if (action != null) {
            action.run();
        }
        // If no action is found (action == null), it means the provided actionType is unknown or invalid.
        // In this case, throw an IllegalArgumentException to signal an error.
        else {
            throw new IllegalArgumentException("Unknown action type: " + actionType);
        }
    }
}

// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import com.ctre.phoenix6.configs.TalonFXConfiguration;
import com.ctre.phoenix6.controls.VelocityVoltage;
import com.ctre.phoenix6.hardware.TalonFX;
import com.ctre.phoenix6.signals.InvertedValue;
import com.ctre.phoenix6.signals.NeutralModeValue;

import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.FunctionalCommand;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants;
import frc.robot.RobotContainer;

public class FeederSubsystem extends SubsystemBase {

	/* Singleton */
	
	private static FeederSubsystem instance = null;
	
	public static FeederSubsystem getInstance() {
		if (instance == null) instance = new FeederSubsystem();
		return instance;
	}

	/* Implementation */

    public final TalonFX feederMotorLeft;
    private final TalonFX feederMotorRight;

    private final VelocityVoltage flControl;
    private final VelocityVoltage frControl;


    private double speed = 0;

    private FeederSubsystem() {
        this.feederMotorLeft = new TalonFX(Constants.Feeder.leftFeederID, "rio");
        this.feederMotorRight = new TalonFX(Constants.Feeder.rightFeederID, "rio");
        this.flControl = new VelocityVoltage(0).withEnableFOC(true);
        this.frControl = new VelocityVoltage(0).withEnableFOC(true);

        applyConfigs();
    }

    private void applyConfigs() {
		TalonFXConfiguration feederMotorConfig = new TalonFXConfiguration();
		feederMotorConfig.MotorOutput.NeutralMode = NeutralModeValue.Brake;
		feederMotorConfig.MotorOutput.Inverted = InvertedValue.Clockwise_Positive;
		feederMotorConfig.Voltage.PeakForwardVoltage = Constants.Intake.peakForwardVoltage;
		feederMotorConfig.Voltage.PeakReverseVoltage = Constants.Intake.peakReverseVoltage;
        
		feederMotorConfig.Slot0.kP = Constants.Shooter.kP;
		feederMotorConfig.Slot0.kI = Constants.Shooter.kI;
		feederMotorConfig.Slot0.kD = Constants.Shooter.kD;
		feederMotorConfig.Slot0.kS = Constants.Shooter.kS;
		feederMotorConfig.Slot0.kV = 1.0 / toRPS(Constants.Shooter.RPMsPerVolt);
		feederMotorConfig.Slot0.kA = 0.0;
		feederMotorConfig.Slot0.kG = 0.0;
        
		this.feederMotorLeft.getConfigurator().apply(feederMotorConfig);
		this.feederMotorRight.getConfigurator().apply(feederMotorConfig);
	}

    public void setVelocity(double speedRPM) {
        // Only set the speed if it's not already the speed.
        if (speedRPM == this.speed) return;

        this.speed = speedRPM;
		this.feederMotorLeft.setControl(flControl.withVelocity(toRPS(speedRPM)));
		this.feederMotorRight.setControl(frControl.withVelocity(toRPS(-speedRPM)));
	}

    private double toRPS(double rpm) {
		return rpm / 60.0;
	}

    public Command intake() {
        return new FunctionalCommand(
            () -> setVelocity(-900), 
            () -> {},
            interrupted -> stop(),
            () -> !RobotContainer.hasIndexedNote,
            this
        );
    }

    public void eject() {
       
        setVelocity(500);
    }

    public void stop() {
        setVelocity(0);
    }

    public double getTorqueCurrent() {
        double sum = 0;
        sum += feederMotorLeft.getTorqueCurrent().getValueAsDouble();
        sum += feederMotorRight.getTorqueCurrent().getValueAsDouble();
        sum /= 2.0;

        return sum;
    }
}
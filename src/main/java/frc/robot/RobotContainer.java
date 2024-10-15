// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.VecBuilder;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Filesystem;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import frc.robot.subsystems.FeederSubsystem;
import frc.robot.subsystems.IntakeSubsystem;
import frc.robot.subsystems.ShooterSubsystem;
import frc.robot.subsystems.SwerveSubsystem;
import frc.robot.subsystems.ShooterSubsystem.Speed;
import frc.robot.subsystems.Vision.LimelightHelpers;
import frc.robot.subsystems.Vision.LimelightIntake;
import frc.robot.subsystems.Vision.LimelightShooter;

import java.io.File;
import java.util.logging.Level;

import au.grapplerobotics.ConfigurationFailedException;
import au.grapplerobotics.LaserCan;

/**
 * This class is where the bulk of the robot should be declared. Since Command-based is a "declarative" paradigm, very
 * little robot logic should actually be handled in the {@link Robot} periodic methods (other than the scheduler calls).
 * Instead, the structure of the robot (including subsystems, commands, and trigger mappings) should be declared here.
 */
public class RobotContainer {
	/* Controllers */
	private final CommandXboxController driverController = new CommandXboxController(0);
	private final Joystick operatorController = new Joystick(1);

	/* Subsystems */
	private final SwerveSubsystem swerve = new SwerveSubsystem(new File(Filesystem.getDeployDirectory(), "swerve/neo"));
	private final IntakeSubsystem intake = new IntakeSubsystem();
	private final FeederSubsystem feeder = new FeederSubsystem();
	private final ShooterSubsystem shooter = new ShooterSubsystem();

	public final LimelightShooter limelightShooter = new LimelightShooter("limelight-shooter");
	public final LimelightIntake limelightIntake = new LimelightIntake("limelight-intake");

	private final LaserCan laserCan = new LaserCan(Constants.Feeder.laserCanID);

	/* Robot states */
	public boolean hasNote = false;
	private State robotState = State.IDLE;
	private boolean isNewState = true;
	private Timer shootTimer = new Timer();

	private final Field2d field = new Field2d();
	
	/**
	 * The container for the robot. Contains subsystems, OI devices, and commands.
	 */
	public RobotContainer() {
		DriverStation.silenceJoystickConnectionWarning(true);
		try {
            laserCan.setRangingMode(LaserCan.RangingMode.SHORT);
            laserCan.setRegionOfInterest(new LaserCan.RegionOfInterest(8, 8, 16, 16));
            laserCan.setTimingBudget(LaserCan.TimingBudget.TIMING_BUDGET_20MS);
        } catch (ConfigurationFailedException e) {
            Robot.getLogger().log(Level.SEVERE, "LaserCAN configuration failed!");
        }

		// Press A to Zero Gyro
		driverController.a().onTrue((Commands.runOnce(swerve::zeroGyro)));
		
		SmartDashboard.putNumber("robot/desired distance", Constants.SHOOT_DISTANCE);
		SmartDashboard.putData("robot/field", field);
	}

	public SwerveSubsystem getSwerveSubsystem() {
		return swerve;
	}

	public void updateVision() {

		limelightIntake.updateVisionData();
		limelightShooter.updateVisionData();

		swerve.getSwerveDrive().updateOdometry();

		boolean doRejectUpdate = false;

		LimelightHelpers.SetRobotOrientation("limelight-shooter", swerve.getPose().getRotation().getDegrees(), 0, 0, 0, 0, 0);
		LimelightHelpers.PoseEstimate mt2 = LimelightHelpers.getBotPoseEstimate_wpiBlue("limelight-shooter");
		
		// if our angular velocity is greater than 720 degrees per second, ignore vision updates
		if (Math.abs(swerve.getRate()) > 720) {
			doRejectUpdate = true;
		}
		if(mt2.tagCount == 0) {
			doRejectUpdate = true;
		}
		if(!doRejectUpdate) {
			swerve.getSwerveDrive().addVisionMeasurement(mt2.pose, mt2.timestampSeconds, Constants.VISION_MEASUREMENT_STD_DEV);
		}

		field.setRobotPose(swerve.getSwerveDrive().getPose());
  	}

	public enum State {
		IDLE,
		INTAKING,
		OUTTAKING,
		PREPPING,
		SHOOTING
	}

	public void setRobotState(State state) {
		// Already on the same state
		if (robotState == state) {
			return;
		}

		robotState = state;

		SmartDashboard.putString("robot/robot state", robotState.toString());
	}

	public void updateAutonState(){
		updateNoteStatus();

		if (hasNote) {
			if (robotState == State.PREPPING || robotState == State.SHOOTING) {
				return;
			}
			setRobotState(State.PREPPING);
		} else {
			setRobotState(State.INTAKING);
		}
	}

	/**
	 * Makes the robot move. Contains code to control auto-aim button presses
	 * @param fieldRelative whether or not the robot is driving in field oriented mode
	 */
	public void autonDrive(boolean fieldRelative) {
		double xSpeed = MathUtil.applyDeadband(driverController.getLeftY(), 0.02) * swerve.getMaximumVelocity();
		double ySpeed = MathUtil.applyDeadband(driverController.getLeftX(), 0.02) * swerve.getMaximumVelocity();
		double rotationalSpeed = -MathUtil.applyDeadband(driverController.getRightX(), 0.02) * swerve.getMaximumAngularVelocity();

		switch (robotState) {
			case INTAKING:
				shooter.stop();

				if (hasNote) {
					intake.stop();
					feeder.stop();
					robotState = State.IDLE;
					break;
				}

				fieldRelative = false;

				if (limelightIntake.hasTarget()) {
					rotationalSpeed = 0;
					ySpeed = limelightIntake.strafe_proportional(Constants.INTAKE_STRAFE_KP);
					System.out.println(limelightIntake.getTX());
					if (Math.abs(ySpeed) < 0.5) {
						xSpeed = -2;
					}			
				} else {
					rotationalSpeed = 0.6;
				}

				intake.intake();
				feeder.intake();

				break;
			case PREPPING:
				feeder.stop();
				intake.stop();
				
				shooter.shoot(Speed.SPEAKER);

				double distanceError = limelightShooter.getDistanceError();

				rotationalSpeed = limelightShooter.aim_proportional(Constants.SPEAKER_AIM_KP);
				xSpeed = limelightShooter.forward_proportional(Constants.SPEAKER_APPROACH_KP);
				fieldRelative = false;

				/*if (limelightShooter.hasTarget()) {
					rotationalSpeed = limelightShooter.aim_proportional(0.02);
					xSpeed = limelightShooter.range_proportional(0.1, SmartDashboard.getNumber("robot/desired distance", 2.2), 57.13);
					fieldRelative = false;
				} else {
					rotationalSpeed = limelightShooter.turn_proportional(swerve.getPose(), 0.025);
				}*/

				if (isReadyToShoot(distanceError, rotationalSpeed)) {
					robotState = State.SHOOTING;
					break;
				}
				break;
			case SHOOTING:
				fieldRelative = false;
				shootTimer.restart();
				feeder.intake();
				if (shootTimer.hasElapsed(Constants.Shooter.postShotTimeout)) {
					robotState = State.IDLE;
					feeder.stop();
				}
				break;
			default:
				break;
		}

		swerve.drive(xSpeed, ySpeed, rotationalSpeed, fieldRelative, 0.02);
	}

	/**
	 * Updates the robot state
	 */
	public void updateState() {
		if (driverController.rightBumper().getAsBoolean() && hasNote) {
			if (robotState == State.PREPPING || robotState == State.SHOOTING) {
				return;
			}
			setRobotState(State.PREPPING);
		} else if (driverController.leftBumper().getAsBoolean() && !hasNote) {
			setRobotState(State.INTAKING);
		} else if (driverController.back().getAsBoolean()){ 
			setRobotState(State.OUTTAKING);
		} else {
			setRobotState(State.IDLE);
		}
	}

	/**
	 * Makes the robot move. Contains code to control auto-aim button presses
	 * @param fieldRelative whether or not the robot is driving in field oriented mode
	 */
	public void drive(boolean fieldRelative) {
		double forwardSpeed = -MathUtil.applyDeadband(driverController.getLeftY(), 0.02) * swerve.getMaximumVelocity();
		double strafeSpeed = -MathUtil.applyDeadband(driverController.getLeftX(), 0.02) * swerve.getMaximumVelocity();
		double rotationalSpeed = -MathUtil.applyDeadband(driverController.getRightX(), 0.02) * swerve.getMaximumAngularVelocity();

		switch (robotState) {
			case OUTTAKING:
				shooter.eject();
				intake.eject();
				feeder.eject();

				break;
			case INTAKING:
				shooter.stop();

				if (hasNote) {
					intake.stop();
					feeder.stop();
					robotState = State.IDLE;
					break;
				}

				fieldRelative = false;

				if (limelightIntake.hasTarget()) {
					rotationalSpeed = 0;
					strafeSpeed = limelightIntake.strafe_proportional(Constants.INTAKE_STRAFE_KP);

					forwardSpeed = -0.005 * (1.0 / Math.abs(strafeSpeed));

					// Cube the forward speed for a smoother start
					if (forwardSpeed <= 1.0)
						Math.pow(forwardSpeed, 3);

					System.out.println(forwardSpeed);
				}
				intake.intake();
				feeder.intake();
				break;
			case PREPPING:
				feeder.stop();
				intake.stop();
				
				shooter.shoot(Speed.SPEAKER);
				if (!limelightShooter.hasTarget()) {
					break;
				}
				double distanceError = limelightShooter.getDistanceError();

				rotationalSpeed = limelightShooter.aim_proportional(Constants.SPEAKER_AIM_KP);
				forwardSpeed = limelightShooter.forward_proportional(Constants.SPEAKER_APPROACH_KP);
				fieldRelative = false;

				if (isReadyToShoot(distanceError, rotationalSpeed)) {
					robotState = State.SHOOTING;
					break;
				}
				break;
			case SHOOTING:
				fieldRelative = false;
				shootTimer.restart();
				feeder.intake();
				if (shootTimer.hasElapsed(Constants.Shooter.postShotTimeout)) {
					robotState = State.IDLE;
					feeder.stop();
				}
				break;
			default:
				robotState = State.IDLE;
				fieldRelative = true;
				shooter.stop();
				intake.stop();
				feeder.stop();
				break;
		}

		swerve.drive(forwardSpeed, strafeSpeed, rotationalSpeed, fieldRelative, 0.02);
	}

	/**
	 * Sets {@link #hasNote} to true if the LaserCAN detects an object, false otherwise
	 * @return returns the status of the note;
	 */
	public void updateNoteStatus() {
		LaserCan.Measurement measurement = laserCan.getMeasurement();

		if (measurement == null || measurement.status != LaserCan.LASERCAN_STATUS_VALID_MEASUREMENT)
			return;

		hasNote = measurement.distance_mm <= 75;
		//SmartDashboard.putNumber("robot/proximityDistance", measurement.distance_mm);
		SmartDashboard.putBoolean("robot/hasNote", hasNote);
	}

	private boolean isReadyToShoot(double distanceError, double rotationalSpeed) {
		System.out.println("distError " + distanceError + " rot " + rotationalSpeed);
		return Math.abs(distanceError) < 0.2 &&
			rotationalSpeed < 0.01 &&
			shooter.isReady(false);
	}
}
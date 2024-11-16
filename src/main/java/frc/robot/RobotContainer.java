// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj.DriverStation;
import edu.wpi.first.wpilibj.Filesystem;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.smartdashboard.Field2d;
import edu.wpi.first.wpilibj.smartdashboard.SendableChooser;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import edu.wpi.first.wpilibj2.command.ParallelRaceGroup;
import edu.wpi.first.wpilibj2.command.button.CommandXboxController;
import edu.wpi.first.wpilibj2.command.button.JoystickButton;
import frc.robot.commands.IndexNote;
import frc.robot.commands.IntakeCommand;
import frc.robot.commands.OuttakeCommand;
import frc.robot.commands.ShootCommand;
import frc.robot.subsystems.FeederSubsystem;
import frc.robot.subsystems.IntakeSubsystem;
import frc.robot.subsystems.LaserCanSubsystem;
import frc.robot.subsystems.ShooterSubsystem;
import frc.robot.subsystems.SwerveSubsystem;
import frc.robot.subsystems.Vision.LimelightHelpers;
import frc.robot.subsystems.Vision.LimelightIntake;
import frc.robot.subsystems.Vision.LimelightShooter;

import java.io.File;
import java.util.logging.Level;

import com.pathplanner.lib.auto.AutoBuilder;

/**
 * This class is where the bulk of the robot should be declared. Since Command-based is a "declarative" paradigm, very
 * little robot logic should actually be handled in the {@link Robot} periodic methods (other than the scheduler calls).
 * Instead, the structure of the robot (including subsystems, commands, and trigger mappings) should be declared here.
 */
public class RobotContainer {
	/* Autonomous */
	private final SendableChooser<Command> autoChooser;

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

	private final LaserCanSubsystem laserCan = new LaserCanSubsystem();

	/* Robot states */
	private boolean hasIndexedNote = false;
	private State robotState = State.IDLE;

	private final Field2d field = new Field2d();
	
	/**
	 * The container for the robot. Contains subsystems, OI devices, and commands.
	 */
	public RobotContainer() {
		registerPathplannerCommands();

		DriverStation.silenceJoystickConnectionWarning(true);

		configureBindings();
	
		SmartDashboard.putData("robot/field", field);

		autoChooser = AutoBuilder.buildAutoChooser();
		SmartDashboard.putData("auto/Auto Chooser", autoChooser);

		swerve.setDefaultCommand(
			swerve.driveCommand(
				() -> -MathUtil.applyDeadband(driverController.getLeftY(), 0.02),
				() -> -MathUtil.applyDeadband(driverController.getLeftX(), 0.02),
				() -> -MathUtil.applyDeadband(driverController.getRightY(), 0.02)
			)
		);
	}

	private void configureBindings() {
		// Press A to Zero Gyro
		driverController.a().onTrue((Commands.runOnce(swerve::zeroGyro)));

		// back button (not B button)
		new JoystickButton(operatorController, 7)
			.whileTrue(new OuttakeCommand(shooter, feeder, intake));

		// start button -> intake
		new JoystickButton(operatorController, 8)
			.whileTrue(new IntakeCommand(intake, feeder));

		// right bumper -> shoot
		new JoystickButton(operatorController, 6)
			.whileTrue(new ShootCommand(shooter));		

		// left bumper -> intake note, then index it
		new JoystickButton(operatorController, 5)
		.whileTrue(
			Commands.runOnce(() -> setRobotState(State.INTAKING))
			.andThen(
				new IntakeCommand(intake, feeder)
				.onlyWhile(() -> !laserCan.hasObject())
				.onlyWhile(() -> feeder.getTorqueCurrent() > -25)
			)
			.andThen(new IndexNote(feeder, laserCan))
			.andThen(() -> setRobotState(State.REVVING))
			.handleInterrupt(() -> setRobotState(State.IDLE))
		);
	}

	public SwerveSubsystem getSwerveSubsystem() {
		return swerve;
	}

	private void registerPathplannerCommands() {
	}

	public Command getAutonomousCommand() {
		return autoChooser.getSelected();
	}

	public void updateVision() {
		limelightIntake.updateVisionData();
		limelightShooter.updateVisionData();

		swerve.getSwerveDrive().updateOdometry();

		boolean doRejectUpdate = false;

		LimelightHelpers.SetRobotOrientation("limelight-shooter", swerve.getPose().getRotation().getDegrees(), 0, 0, 0, 0, 0);
		LimelightHelpers.PoseEstimate mt2 = LimelightHelpers.getBotPoseEstimate_wpiBlue("limelight-shooter");
		
		try {
			// if our angular velocity is greater than 720 degrees per second, ignore vision updates
			if (Math.abs(swerve.getRate()) > 720) {
				doRejectUpdate = true;
			} else if(mt2.tagCount == 0) {
				doRejectUpdate = true;
			}
			
			if(!doRejectUpdate) {
				//double estimatedRotation = -mt2.pose.getRotation().getDegrees();
				//swerve.getSwerveDrive().setGyro(new Rotation3d(estimatedRotation, 0, 0));
				swerve.getSwerveDrive().addVisionMeasurement(mt2.pose, mt2.timestampSeconds, Constants.VISION_MEASUREMENT_STD_DEV);
			}

			SmartDashboard.putNumber("robot/estimated rotation", mt2.pose.getRotation().getDegrees());
		} catch (Exception e) {
			Robot.getLogger().log(Level.SEVERE, e.getMessage());
		}

		field.setRobotPose(swerve.getSwerveDrive().getPose());
  	}

	public enum State {
		IDLE,
		INTAKING,
		OUTTAKING,
		REVVING,
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

	public void updateNoteStatus() {
		this.hasIndexedNote = laserCan.isNoteIndexed();
	}
}
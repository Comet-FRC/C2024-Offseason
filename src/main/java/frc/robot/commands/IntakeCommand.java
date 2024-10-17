package frc.robot.commands;

import au.grapplerobotics.LaserCan;
import edu.wpi.first.math.MathUtil;
import edu.wpi.first.wpilibj2.command.Command;
import frc.robot.Constants;
import frc.robot.Robot;
import frc.robot.RobotContainer;
import frc.robot.RobotContainer.State;
import frc.robot.subsystems.FeederSubsystem;
import frc.robot.subsystems.IntakeSubsystem;
import frc.robot.subsystems.SwerveSubsystem;
import frc.robot.subsystems.Vision.LimelightIntake;

public class IntakeCommand extends Command {
    private final IntakeSubsystem intake;
    private final FeederSubsystem feeder;
    private final LimelightIntake limelight;
    private final SwerveSubsystem swerve;

    private RobotContainer robotContainer;

    public IntakeCommand(SwerveSubsystem swerve,
        IntakeSubsystem intake,
        FeederSubsystem feeder,
        LimelightIntake limelight,
        LaserCan laserCan)
    {
        this.swerve = swerve;
        this.intake = intake;
        this.feeder = feeder;
        this.limelight = limelight;
    }

    @Override
    public void initialize() {
        robotContainer = Robot.getInstance().getRobotContainer();
        robotContainer.setRobotState(State.INTAKING);
        intake.intake();
        feeder.intake();
    }

    @Override
    public void execute() {
        if (limelight.hasTarget()) {
            robotContainer.overrideRotationalSpeed(0);

            double strafeSpeed = limelight.strafe_proportional(Constants.INTAKE_STRAFE_KP);
            robotContainer.overrideStrafeSpeed(strafeSpeed);

            if (swerve.getSwerveDrive().getRobotVelocity().vyMetersPerSecond < Constants.INTAKE_STRAFE_THRESHOLD &&
                Math.abs(limelight.getTX()) < 5) 
            {
                robotContainer.setForwardSpeedOverride(-2);
            } 
        }
    }

    @Override
    public boolean isFinished() {
        return robotContainer.hasNote;
    }

    @Override
    public void end(boolean interrupted) {
        //robotContainer.setNoteStatus(true);
        intake.stop();
        feeder.stop();
        robotContainer.setRobotState(State.IDLE);
    }
}

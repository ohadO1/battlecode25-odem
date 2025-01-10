package odemplayer;

import battlecode.common.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class RobotPlayer {

  static int turnCount = 0;
  static final Random rng = new Random();

  static final Direction[] directions = {
      Direction.NORTH,
      Direction.NORTHEAST,
      Direction.EAST,
      Direction.SOUTHEAST,
      Direction.SOUTH,
      Direction.SOUTHWEST,
      Direction.WEST,
      Direction.NORTHWEST,
  };

  public static void run(RobotController rc) throws GameActionException {
    while (true) {
      turnCount++;
      try {
        switch (rc.getType()) {
          case SOLDIER:
            runSoldier(rc);
            break;
          case MOPPER:
            runMopper(rc);
            break;
          case SPLASHER:
            runSplasher(rc);
            break;

          default:
            runTower(rc);
            break;
        }

      } catch (GameActionException e) {
        System.out.println("GAME ACTION EXCEPTION");
        e.printStackTrace();
      } finally {
        Clock.yield();
      }

    }

  }

  public static void runTower(RobotController rc) throws GameActionException {
    if (rc.canUpgradeTower(rc.getLocation())) {
      rc.upgradeTower(rc.getLocation());
    }

    Direction dir = directions[rng.nextInt(directions.length)];
    MapLocation nextLocation = rc.getLocation().add(dir);
    int robotType = rng.nextInt(3);

    if (robotType == 0 && rc.canBuildRobot(UnitType.SOLDIER, nextLocation)) {
      rc.buildRobot(UnitType.SOLDIER, nextLocation);
      System.out.println("BUILT A SOLDIRE");
    }

    else if (robotType == 1 && rc.canBuildRobot(UnitType.SPLASHER, nextLocation)) {
      rc.buildRobot(UnitType.SPLASHER, nextLocation);
      System.out.println("BUILT A SPLASHER");
    }

    else if (robotType == 2 && rc.canBuildRobot(UnitType.MOPPER, nextLocation)) {
      rc.buildRobot(UnitType.MOPPER, nextLocation);
      System.out.println("BUILT A MOPPER");
    }

    RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
    for (RobotInfo robot : nearbyRobots) {
      if (rc.canAttack(robot.getLocation())) {
        rc.attack(robot.getLocation());
      }
    }
  }

  public static void runSoldier(RobotController rc) throws GameActionException {
    MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
    MapInfo currentRuin = null;

    for (MapInfo tile : nearbyTiles) {
      if (tile.hasRuin()) {
        currentRuin = tile;
      }
    }

    if (currentRuin != null) {
      MapLocation targetLocation = currentRuin.getMapLocation();
      Direction dir = rc.getLocation().directionTo(targetLocation);
      if (rc.canMove(dir)) {
        rc.move(dir);
      }

      MapLocation checkMarked = targetLocation.subtract(dir);
      if (rc.senseMapInfo(checkMarked).getMark() == PaintType.EMPTY
          && rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLocation)) {
        rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLocation);
      }

      for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLocation, 8)) {
        if (patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY) {
          if (rc.canAttack(patternTile.getMapLocation())) {
            if (patternTile.getMark() == PaintType.ALLY_SECONDARY) {
              rc.attack(patternTile.getMapLocation(), true);

            } else {
              rc.attack(patternTile.getMapLocation(), false);
            }
          }
        }
      }

      if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLocation)) {
        rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLocation);
      }

    }

    Direction dir = directions[rng.nextInt(directions.length)];
    MapLocation nextLoc = rc.getLocation().add(dir);
    if (rc.canMove(dir)) {
      rc.move(dir);
    }

    if (rc.canAttack(nextLoc)) {
      MapInfo nextLocInfo = rc.senseMapInfo(nextLoc);
      if (!nextLocInfo.getPaint().isAlly()) {
        rc.attack(nextLoc);
      }
    }

  }

  public static void runMopper(RobotController rc) throws GameActionException {
    Direction dir = directions[rng.nextInt(directions.length)];
    MapLocation nextLoc = rc.getLocation().add(dir);
    if (rc.canMove(dir)) {
      rc.move(dir);
    }

    if (rc.canMopSwing(dir)) {
      rc.mopSwing(dir);
    } else if (rc.canAttack(nextLoc)) {
      rc.attack(nextLoc);
    }
  }

  public static void runSplasher(RobotController rc) throws GameActionException {
    Direction dir = directions[rng.nextInt(directions.length)];
    MapLocation nextLoc = rc.getLocation().add(dir);

    if (rc.canMove(dir)) {
      rc.move(dir);
    }
    if (rc.canAttack(nextLoc)) {
      rc.attack(nextLoc);
    }
  }
}

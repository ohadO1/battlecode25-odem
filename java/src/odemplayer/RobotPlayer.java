package odemplayer;

import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class RobotPlayer {

  private enum MessageType {
    SAVE_CHIPS
  }

  static int turnCount = 0;

  // messanger:
  static boolean isMessanger = false;

  static ArrayList<MapLocation> knownTowers = new ArrayList<>();

  static boolean isSaving = false;
  //

  // tower
  static int savingTurns = 0;

  //

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

    if (rc.getType() == UnitType.MOPPER && rc.getID() % 2 == 0) {
      System.out.println(rc.getID());
      isMessanger = true;
    }

    while (true) {
      turnCount++;

      // if a mopper is a messanger

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

    if (savingTurns == 0) {
      isSaving = false;
      if (rc.canUpgradeTower(rc.getLocation())) {
        rc.upgradeTower(rc.getLocation());
      }

      Direction dir = directions[rng.nextInt(directions.length)];
      MapLocation nextLocation = rc.getLocation().add(dir);
      int robotType = rng.nextInt(3);

      if (robotType == 0 && rc.canBuildRobot(UnitType.SOLDIER, nextLocation)) {
        rc.buildRobot(UnitType.SOLDIER, nextLocation);
        System.out.println("BUILT A SOLDIER");
      }

      else if (robotType == 1 && rc.canBuildRobot(UnitType.SPLASHER, nextLocation)) {
        rc.buildRobot(UnitType.SPLASHER, nextLocation);
        System.out.println("BUILT A SPLASHER");
      }

      else if (robotType == 2 && rc.canBuildRobot(UnitType.MOPPER, nextLocation)) {
        rc.buildRobot(UnitType.MOPPER, nextLocation);
        System.out.println("BUILT A MOPPER");
      }
    } else {
      savingTurns--;
      rc.setIndicatorString("Saving For " + savingTurns + "More Turns");
    }

    RobotInfo[] nearbyRobots = rc.senseNearbyRobots();
    for (RobotInfo robot : nearbyRobots) {
      if (rc.canAttack(robot.getLocation())) {
        rc.attack(robot.getLocation());
      }
    }

    // Read incoming messages
    Message[] messages = rc.readMessages(-1);
    for (Message m : messages) {
      System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());

      if (m.getBytes() == MessageType.SAVE_CHIPS.ordinal() && !isSaving) {
        // TODO: Make more specific
        savingTurns = 50;
        isSaving = true;
      }
    }
  }

  public static void runSoldier(RobotController rc) throws GameActionException {
    MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
    MapInfo currentRuin = null;
    int currentDistance = 99999;

    for (MapInfo tile : nearbyTiles) {
      if (tile.hasRuin()) {
        int distance = tile.getMapLocation().distanceSquaredTo(rc.getLocation());
        if (distance < currentDistance) {
          currentRuin = tile;
          currentDistance = distance;
        }
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
    if (isMessanger == true) {
      System.out.println("IN IF STATEMENT, MARKING");
      rc.setIndicatorDot(rc.getLocation(), 0, 255, 0);
    }

    if (isMessanger && isSaving && knownTowers.size() > 0) {
      // TODO: find closest tower and go there
      MapLocation destination = knownTowers.get(0);
      Direction dir = rc.getLocation().directionTo(destination);
      // TODO: what happenes if mopper is facing a wall?
      if (rc.canMove(dir)) {
        rc.move(dir);
      }
    }

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

    // if we are a messanger, we also want to update friendly towers and check for
    // ruins
    if (isMessanger) {
      updateFriendlyTowers(rc);
      checkNearbyRuins(rc);
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

  public static void updateFriendlyTowers(RobotController rc) throws GameActionException {
    // Search for all nearby robots
    RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
    for (RobotInfo ally : allyRobots) {
      if (!ally.getType().isTowerType())
        continue;

      // TODO: what to do with known towers
      MapLocation allyLocation = ally.location;
      if (knownTowers.contains(allyLocation)) {
        if (isSaving) {
          if (rc.canSendMessage(allyLocation)) {
            rc.sendMessage(allyLocation, MessageType.SAVE_CHIPS.ordinal());
          }
          isSaving = false;
        }

        continue;
      }

      knownTowers.add(allyLocation);
    }

  }

  public static void checkNearbyRuins(RobotController rc) throws GameActionException {
    MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

    for (MapInfo tile : nearbyTiles) {

      if (!tile.hasRuin())
        continue;

      if (rc.senseRobotAtLocation(tile.getMapLocation()) != null) {
        continue;
      }

      Direction dir = tile.getMapLocation().directionTo(rc.getLocation());
      MapLocation markTile = tile.getMapLocation().add(dir);

      if (!rc.senseMapInfo(markTile).getMark().isAlly()) {
        continue;
      }

      // check if there is a ruin but there is no robot on top of the ruin (tower)
      isSaving = true;
    }

  }

}

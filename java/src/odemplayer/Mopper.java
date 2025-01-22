package odemplayer;

import java.util.ArrayList;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.*;

public class Mopper extends Globals {

  private enum MOPPER_ROLES {
    messenger,
    refiller,
    normal,
    attack
  }

  private enum MOPPER_STATE {
    transferPaint,
    // im removing this for now (commenting out), moppers are inside the painted
    // territory anyway
    notifyTower,
    roam,
    attackEnemy,
    attackTile,
    refillAlly,
    enemyDetected
  }

  static MOPPER_TASKS task = null;

  enum MOPPER_TASKS {
    transferPaint,
    attack,
    remove_enemy_paint
  }

  private static MOPPER_ROLES role = MOPPER_ROLES.normal;
  private static MapLocation tileToAttack = null;
  private static MapLocation ruinDest = null;
  private static MOPPER_STATE state = MOPPER_STATE.roam;
  static MOPPER_STATE statePrev = state;
  private static MapLocation towerDestination = null;
  static boolean stateChanged = false;
  static RobotInfo allyToRefill = null;
  static int refillWait = 0;
  static RobotInfo refillTower;

  public static void runMopper(RobotController rc) throws GameActionException {
    Utils.updateFriendlyTowers(rc);
    // TODO: add a role that will support soldiers and refill them from tower
    // contantly
    // TODO: add check if task != null

    // mopswing enemies - move to state
    for (MapInfo tile : rc.senseNearbyMapInfos()) {
      RobotInfo potentialEnemy = rc.senseRobotAtLocation(tile.getMapLocation());
      if (potentialEnemy != null && (potentialEnemy.team != rc.getTeam())) {
        if (rc.canMopSwing(rc.getLocation().directionTo(potentialEnemy.getLocation()))) {
          rc.mopSwing(rc.getLocation().directionTo(potentialEnemy.getLocation()));
        }
      }
      if (tileToAttack == null && tile.getPaint().isEnemy()) {
        state = MOPPER_STATE.attackTile;
        tileToAttack = tile.getMapLocation();
      }
    }

    // transfer paint
    if (allyToRefill == null) {
      for (RobotInfo robot : rc.senseNearbyRobots()) {
        if ((robot.getType() == UnitType.SOLDIER || robot.getType() == UnitType.SPLASHER
            || robot.getType() == UnitType.MOPPER) && robot.team == rc.getTeam()) {
          if (((double) robot.getPaintAmount()) / robot.getType().paintCapacity <= 0.3 || rc.getPaint() <= 30) {
            state = MOPPER_STATE.refillAlly;
            allyToRefill = robot;
          }
        }
      }
    }

    switch (state) {
      case roam:
        // remain only in our tiles
        Utils.mopperRoam(rc);

        // if (ruinDest != null && knownTowersInfos.size() > 0) {
        // ruinDest = checkNearbyRuins(rc);
        // state = MOPPER_STATE.notifyTower;
        // towerDestination = Utils.findClosestTower(knownTowersInfos, rc);
        // }
        break;

      case refillAlly:
        rc.setIndicatorString("in refillAlly");
        int numToTransfer = rc.getPaint() > 60 ? 50 : rc.getPaint();
        if (rc.canTransferPaint(allyToRefill.location, numToTransfer)) {
          rc.transferPaint(allyToRefill.getLocation(), numToTransfer);
          allyToRefill = null;
          state = MOPPER_STATE.roam;
        } else {
          PathFinder.moveToLocation(rc, allyToRefill.getLocation());
        }
        break;

      case enemyDetected:
        mopperAttack(rc, rc.getLocation());
        state = MOPPER_STATE.roam;
        break;

      case attackTile:
        rc.setIndicatorString("attackTile");
        if (!rc.canAttack(tileToAttack)) {
          PathFinder.moveToLocation(rc, tileToAttack);
        }
        else rc.attack(tileToAttack);
        tileToAttack = null;
        state = MOPPER_STATE.roam;
        break;

      default:
        break;
    }
  }

  // TODO: change it
  public static void mopperAttack(RobotController rc, MapLocation nextLoc) throws GameActionException {
    for (Direction tryMopDirection : directions) {
      if (rc.canMopSwing(tryMopDirection)) {
        rc.mopSwing(tryMopDirection);
      }
    }
    if (rc.canAttack(nextLoc)) {
      rc.attack(nextLoc);
    }

  }

  public static MapLocation checkNearbyRuins(RobotController rc) throws GameActionException {
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

      // found not occupied ruin
      return tile.getMapLocation();
    }
    return null;
  }

  public static void determineMopperRole(RobotController rc) {
    int id = rc.getID();
    switch (id % 2) {
      case 0:
        Mopper.role = MOPPER_ROLES.messenger;
      default:
        Mopper.role = MOPPER_ROLES.normal;
    }
  }

  /**
   * @param - how much paint to take or give to the unit (positive to give,
   *          negative to take)
   * @param - targetLocation
   * @return void
   */
  public static void tranferPaintToLocation(RobotController rc,
      MapLocation targetLocation, int amount) throws GameActionException {

    if (rc.canTransferPaint(targetLocation, amount)) {
      rc.transferPaint(targetLocation, amount);
      return;
    }

    PathFinder.moveToLocation(rc, targetLocation);
  }

}

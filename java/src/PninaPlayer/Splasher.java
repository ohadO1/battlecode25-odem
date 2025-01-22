package PninaPlayer;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;
import PninaPlayer.Soldier.SOLDIER_STATES;

public class Splasher extends Globals {
  private enum SPLASHER_STATE {
    roam,
    waitForRefill,
    MoveToAttackTower,
    ATTACK_TOWER,
    seekRefill
  }
  private static int refillWait;
  static int stateChangedRunAt = 0;
  static RobotInfo refillTower;
  private static SPLASHER_STATE state = SPLASHER_STATE.roam;
  private static SPLASHER_STATE prevState = state;
  private static boolean stateChanged = false;

  // Target variables
  private static MapLocation enemyTowerLocation = null;
  private static MapLocation nearestAllyPaintTower = null;


  // Paint thresholds
  private static final double PAINT_THRESHOLD_ATTACK = 50; // Minimum paint required to perform an attack
  private static final double PAINT_THRESHOLD_REFILL = 100; // Threshold to seek refill
  private static final double PAINT_CRITICAL_LEVEL = 50; // Critical level to avoid entering combat
  private static int actionCooldown = 50; // Splasher's attack cooldown
  public static void runSplasher(RobotController rc) throws GameActionException {
    // Initialize any necessary variables
    Utils.updateFriendlyTowers(rc);

      try {
        stateChanged = false;
        double currentPaint = rc.getPaint();
        MapLocation currentLocation = rc.getLocation();

        switch (state) {
            case roam:
                enemyTowerLocation = findNearestEnemyTower(rc);

                if (enemyTowerLocation != null) {
                    state = SPLASHER_STATE.MoveToAttackTower;
                } else {
                    // Random movement while painting
                    roamAndPaint(rc);
                }
                // Check paint levels
                refillTower = null;
                if(((double)rc.getPaint())/rc.getType().paintCapacity < SOLDIER_PAINT_FOR_CASUAL_REFILL)
                state = SPLASHER_STATE.seekRefill;
                break;
            case seekRefill:
                //abort
                if((double)rc.getPaint()/rc.getType().paintCapacity > SOLDIER_PAINT_FOR_CASUAL_REFILL)
                state = SPLASHER_STATE.roam;

                int missingPaint = rc.getType().paintCapacity - rc.getPaint();
                if(stateChanged || refillTower == null) {
                stateChangedRunAt = rc.getRoundNum();
        //          System.out.print("looking for tower ... ");
                refillTower = null;

                for(RobotInfo tower : knownTowersInfos){
                    if(refillTower == null ||
                        (tower.getLocation().distanceSquaredTo(rc.getLocation()) < refillTower.getLocation().distanceSquaredTo(rc.getLocation()))
                        && tower.getPaintAmount() >= missingPaint)
                    refillTower = tower;
                }
                //no tower has enough paint. just go to the nearest and wait there.
                if(refillTower == null) refillTower = Utils.findClosestTowerInfo(knownTowersInfos,rc);
                }
                if(refillTower == null) System.out.println("still didnt find any tower. state changed run at: " + stateChangedRunAt);
                //move
                PathFinder.moveToLocation(rc,refillTower.getLocation());
                //am i there yet?
                if(!rc.canTransferPaint(refillTower.getLocation(),0)){

                if(rc.canTransferPaint(refillTower.getLocation(),-missingPaint))
                {
                    rc.transferPaint(refillTower.getLocation(),-missingPaint);
                    state = SPLASHER_STATE.roam;
                }
                }

      break;
      //endregion
      //region attack towers
            case waitForRefill:
              if(stateChanged) refillWait = 0;
              refillWait++;
              if(refillWait%10 == 0) rc.setIndicatorString("waiting for refill for " + refillWait + " turns.");
              break;
              /* TODO: change wait for refill the go to tower and refill for all bots
                // Find nearest allied paint tower
                nearestAllyPaintTower = findNearestAllyPaintTower(rc);

                if (nearestAllyPaintTower != null) {
                    // Move towards the paint tower
                    moveToLocation(rc, nearestAllyPaintTower);
                    if (rc.canWithdrawPaint(nearestAllyPaintTower, rc.getType().paintCapacity - (int)currentPaint)) {
                        rc.withdrawPaint(nearestAllyPaintTower, rc.getType().paintCapacity - (int)currentPaint);
                        state = SPLASHER_STATE.roam;
                    }
                } else {
                    // if no tower found, roam around and paint
                    roamAndPaint(rc);
                }
                break; */

            case MoveToAttackTower:
                if (enemyTowerLocation == null) {
                    // Lost track of enemy tower, return to roam
                    state = SPLASHER_STATE.roam;
                    break;
                }
                // Check paint levels
                if(((double)rc.getPaint())/rc.getType().paintCapacity < SOLDIER_PAINT_FOR_CASUAL_REFILL)
                state = SPLASHER_STATE.seekRefill;
                // Move towards the enemy tower
                if (rc.getLocation().distanceSquaredTo(enemyTowerLocation) <= rc.getType().actionRadiusSquared) {
                    state = SPLASHER_STATE.ATTACK_TOWER;
                } else {
                    moveToLocation(rc, enemyTowerLocation);
                }

                // Check paint levels again because we might have used paint while moving
                if (currentPaint < PAINT_THRESHOLD_ATTACK) {
                    state = SPLASHER_STATE.waitForRefill;
                }
                break;

            case ATTACK_TOWER:
                while (rc.isActionReady() && rc.canAttack(enemyTowerLocation)) {
                        rc.attack(enemyTowerLocation);
                        // Update paint amount
                        currentPaint -= PAINT_THRESHOLD_ATTACK;
                    }
                  
                break;


            default: 
                state = SPLASHER_STATE.roam;
                break;
          }
        stateChanged = (prevState != state);
        prevState = state;
      } catch (Exception e) {
        System.out.println("Splasher Exception");
        e.printStackTrace();
      }
      
  }

  /**
   * Finds the nearest enemy tower location.
   *
   * @param rc The RobotController instance.
   * @return The MapLocation of the nearest enemy tower, or null if none found.
   * @throws GameActionException
   */
  private static MapLocation findNearestEnemyTower(RobotController rc) throws GameActionException {
      RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
      MapLocation myLocation = rc.getLocation();
      MapLocation nearestTowerLocation = null;
      int minDistance = Integer.MAX_VALUE;

      for (RobotInfo ri : nearbyEnemies) {
          if (ri.getType() == UnitType.LEVEL_ONE_PAINT_TOWER ) {
              int distance = myLocation.distanceSquaredTo(ri.getLocation());
              if (distance < minDistance) {
                  minDistance = distance;
                  nearestTowerLocation = ri.getLocation();
              }
          }
      }

      // If no tower found nearby, share known tower locations from team communication (if implemented)

      return nearestTowerLocation;
  }

  /**
   * Finds the nearest allied paint tower to refill paint.
   *
   * @param rc The RobotController instance.
   * @return The MapLocation of the nearest allied paint tower, or null if none found.
   * @throws GameActionException
   */
  private static MapLocation findNearestAllyPaintTower(RobotController rc) throws GameActionException {
      // Update known towers
      Utils.updateFriendlyTowers(rc);

      MapLocation myLocation = rc.getLocation();
      MapLocation nearestPaintTower = null;
      int minDistance = Integer.MAX_VALUE;

      for (RobotInfo towerInfo : knownTowersInfos) {
          if (towerInfo.getType() == UnitType.LEVEL_ONE_PAINT_TOWER ||
              towerInfo.getType() == UnitType.LEVEL_TWO_PAINT_TOWER ||
              towerInfo.getType() == UnitType.LEVEL_THREE_PAINT_TOWER) {

              int distance = myLocation.distanceSquaredTo(towerInfo.getLocation());
              if (distance < minDistance) {
                  minDistance = distance;
                  nearestPaintTower = towerInfo.getLocation();
              }
          }
      }

      return nearestPaintTower;
  }

  /**
   * Moves the splasher towards the specified location using pathfinding.
   *
   * @param rc             The RobotController instance.
   * @param targetLocation The target MapLocation to move towards.
   * @throws GameActionException
   */
  private static void moveToLocation(RobotController rc, MapLocation targetLocation) throws GameActionException {
      // Use your existing PathFinder implementation
      PathFinder.moveToLocation(rc, targetLocation);
  }

  /**
   * Roams around randomly and paints the current location if possible.
   *
   * @param rc The RobotController instance.
   * @throws GameActionException
   */
  private static void roamAndPaint(RobotController rc) throws GameActionException {
    
      if (rc.isMovementReady()) {
        int choice = (int) (Math.random() * 3);
        switch (choice) {
            case 0:     Utils.roamGracefullyf(rc);         break;
            case 1:     Utils.roamCircle(rc);              break;
            case 3:
                Direction randomDirection = Utils.getRandomDirection(rc);
                if (rc.canMove(randomDirection)) {
                    rc.move(randomDirection);
                }


    }
      

      // Paint the current location if it's not already painted by allies
      if (rc.isActionReady() && rc.canAttack(rc.getLocation())) {
          MapInfo currentMapInfo = rc.senseMapInfo(rc.getLocation());
          if (currentMapInfo.getPaint() != PaintType.ALLY_PRIMARY &&
              currentMapInfo.getPaint() != PaintType.ALLY_SECONDARY) {

              rc.attack(rc.getLocation());
          }
      }
  }

  }
}

package game;

import game.button.*;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import static game.CreatureID.BLOCK;
import static game.CreatureID.*;
import static game.CreatureID.CHIP;
import static game.Direction.*;
import static game.Tile.*;

/**
 * Creatures are encoded as follows:
 *
 *       0 0    | 0 0 0 0 | 0 0 0 0 0 | 0 0 0 0 0
 *    DIRECTION | MONSTER |    ROW    |    COL
 */
public class Creature{

    private Position position;
    private CreatureID creatureType;
    private Direction direction;
    private boolean sliding;

    private Direction nextMoveDirectionCheat = null;

    // Direction-related methods

    public Direction getDirection(){
        return direction;
    }
    protected void setDirection(Direction direction){
        this.direction = direction;
    }
    protected void setPosition(Position position){ //So you can make a creature teleport 32 tiles at once to data reset properly
        this.position = position;
    }
    
    Direction[] getDirectionPriority(Creature chip, RNG rng){
        if (nextMoveDirectionCheat != null) {
            Direction[] directions = new Direction[] {nextMoveDirectionCheat};
            nextMoveDirectionCheat = null;
            if (creatureType == WALKER || creatureType == BLOB) rng.random4();
            return directions;
        }
        if (isSliding()) return direction.turn(new Direction[] {TURN_FORWARD, TURN_AROUND});
        switch (creatureType){
            case BUG: return direction.turn(new Direction[] {TURN_LEFT, TURN_FORWARD, TURN_RIGHT, TURN_AROUND});
            case FIREBALL: return direction.turn(new Direction[] {TURN_FORWARD, TURN_RIGHT, TURN_LEFT, TURN_AROUND});
            case PINK_BALL: return direction.turn(new Direction[] {TURN_FORWARD, TURN_AROUND});
            case TANK_STATIONARY: return new Direction[] {};
            case GLIDER: return direction.turn(new Direction[] {TURN_FORWARD, TURN_LEFT, TURN_RIGHT, TURN_AROUND});
            case TEETH: return position.seek(chip.position);
            case WALKER:
                Direction[] directions = new Direction[] {TURN_LEFT, TURN_AROUND, TURN_RIGHT};
                rng.randomPermutation3(directions);
                return direction.turn(new Direction[] {TURN_FORWARD, directions[0], directions[1], directions[2]});
            case BLOB:
                directions = new Direction[] {TURN_FORWARD, TURN_LEFT, TURN_AROUND, TURN_RIGHT};
                rng.randomPermutation4(directions);
                return direction.turn(directions);
            case PARAMECIUM: return direction.turn(new Direction[] {TURN_RIGHT, TURN_FORWARD, TURN_LEFT, TURN_AROUND});
            case TANK_MOVING: return new Direction[] {getDirection()};
            default: return new Direction[] {};
        }
    }
    public Direction[] seek(Position position){
        return this.position.seek(position);
    }
    
    private static Direction applySlidingTile(Direction direction, Tile tile, RNG rng){
        switch (tile){
            case FF_DOWN:
                return DOWN;
            case FF_UP:
                return UP;
            case FF_RIGHT:
                return RIGHT;
            case FF_LEFT:
                return LEFT;
            case FF_RANDOM:
                return Direction.fromOrdinal(rng.random4());
            case ICE_SLIDE_SOUTHEAST:
                if (direction == UP) return RIGHT;
                else if (direction == LEFT) return DOWN;
                else return direction;
            case ICE_SLIDE_NORTHEAST:
                if (direction == DOWN) return RIGHT;
                else if (direction == LEFT) return UP;
                else return direction;
            case ICE_SLIDE_NORTHWEST:
                if (direction == DOWN) return LEFT;
                else if (direction == RIGHT) return UP;
                else return direction;
            case ICE_SLIDE_SOUTHWEST:
                if (direction == UP) return LEFT;
                else if (direction == RIGHT) return DOWN;
                else return direction;
            case TRAP:
                return direction;
        }
        return direction;
    }
    Direction[] getSlideDirectionPriority(Tile tile, RNG rng, boolean changeOnRFF){
        if (nextMoveDirectionCheat != null) {
            Direction[] directions = new Direction[] {nextMoveDirectionCheat};
            nextMoveDirectionCheat = null;
            return directions;
        }
        if (tile.isIce() || (creatureType.isChip() && tile == TELEPORT)){
            Direction[] directions = direction.turn(new Direction[] {TURN_FORWARD, TURN_AROUND});
            directions[0] = applySlidingTile(directions[0], tile, rng);
            directions[1] = applySlidingTile(directions[1], tile, rng);
            return directions;
        }
        else if (tile == TELEPORT) return new Direction[] {direction};
        else if (tile == FF_RANDOM && !changeOnRFF) return new Direction[] {direction};
        else return new Direction[] {applySlidingTile(getDirection(), tile, rng)};
    }
    
    public Direction getNextMoveDirectionCheat() {
        return nextMoveDirectionCheat;
    }
    
    public void setNextMoveDirectionCheat(Direction nextMoveDirectionCheat) {
        this.nextMoveDirectionCheat = nextMoveDirectionCheat;
    }
    
    // MonsterType-related methods

    public CreatureID getCreatureType(){
        return creatureType;
    }
    public void setCreatureType(CreatureID creatureType){
        this.creatureType = creatureType;
    }
    void kill(){
        creatureType = DEAD;
    }
    public boolean isDead() {
        return creatureType == DEAD;
    }

    // Position-related methods

    public Position getPosition() {
        return position;
    }
    public void turn (Direction turn) {
        direction = direction.turn(turn);
    }

    // Sliding-related functions

    public boolean isSliding() {
        return creatureType == CHIP_SLIDING || sliding;
    }
    void setSliding(boolean sliding){
        this.sliding = sliding;
    }
    void setSliding(boolean sliding, Level level){
        setSliding(this.sliding, sliding, level);
    }
    void setSliding(boolean wasSliding, boolean isSliding, Level level) {
    
        if (wasSliding && !isSliding){
            if (!isDead() && creatureType.isChip()) setCreatureType(CHIP);
            else level.slipList.remove(this);
        }
        else if (!wasSliding && isSliding){
            if (creatureType.isChip()) setCreatureType(CHIP_SLIDING);
            else if (level.getSlipList().contains(this)) {
                new RuntimeException("adding block twice").printStackTrace();
            }
            else level.getSlipList().add(this);
        }
        if (creatureType.isBlock() && wasSliding && level.layerBG.get(position) == TRAP){
            level.slipList.remove(this);
            level.slipList.add(this);
            this.sliding = true;
        }
        this.sliding = isSliding;
    }

    /**
     * Get the Tile representation of this monster.
     * @return The Tile representation of this monster.
     */
    public Tile toTile(){
        switch (creatureType){
            case BLOCK: return Tile.BLOCK;
            case CHIP_SLIDING: return Tile.fromOrdinal(Tile.CHIP_UP.ordinal() | direction.ordinal());
            case TANK_STATIONARY: return Tile.fromOrdinal(TANK_UP.ordinal() | direction.ordinal());
            default: return Tile.fromOrdinal((creatureType.ordinal() << 2) + 0x40 | direction.ordinal());
        }
    }
    
    
    private void teleport(Direction direction, Level level, Position position, List<Button> pressedButtons) {
        int portalIndex;
        for (portalIndex = 0; true; portalIndex++){
            if (portalIndex >= level.getPortals().length) return;
            if (level.getPortals()[portalIndex].equals(position)){
                break;
            }
        }
        int l = level.getPortals().length;
        int i = portalIndex;
        do{
            i--;
            if (i < 0) i += l;
            position.setIndex(level.getPortals()[i].getIndex());
            if (level.layerFG.get(position) != TELEPORT) continue;
            Position exitPosition = position.move(direction);
            if (exitPosition.getX() < 0 || exitPosition.getX() > 31 ||
                exitPosition.getY() < 0 || exitPosition.getY() > 31) continue;
            Tile exitTile = level.layerFG.get(exitPosition);
            if (!creatureType.isChip() && exitTile.isChip()) exitTile = level.layerBG.get(exitPosition);
            if (creatureType.isChip() && exitTile.isTransparent()) exitTile = level.layerBG.get(exitPosition);
            if (creatureType.isChip() && exitTile == Tile.BLOCK){
                Creature block = new Creature(direction, BLOCK, exitPosition);
                for (Creature m : level.slipList) {
                    if (m.position.equals(exitPosition)){
                        block = m;
                        break;
                    }
                }
                if (canEnter(direction, level.layerBG.get(exitPosition), level) && block.canLeave(direction, level.layerBG.get(exitPosition), level)) {
                    Position blockPushPosition = exitPosition.move(direction);
                    if (blockPushPosition.getX() < 0 || blockPushPosition.getX() > 31 ||
                        blockPushPosition.getY() < 0 || blockPushPosition.getY() > 31) continue;
                    if (block.canEnter(direction, level.layerFG.get(blockPushPosition), level)){
                        if (block.tryMove(direction, level, false, pressedButtons)) break;
                    }
                }
                if (block.tryMove(direction, level, false, pressedButtons)) break;
            }
            if (canEnter(direction, exitTile, level)) break;
        }
        while (i != portalIndex);
    }
    
    private boolean canLeave(Direction direction, Tile tile, Level level){
        switch (tile){
            case THIN_WALL_UP: return direction != UP;
            case THIN_WALL_RIGHT: return direction != RIGHT;
            case THIN_WALL_DOWN: return direction != DOWN;
            case THIN_WALL_LEFT: return direction != LEFT;
            case THIN_WALL_DOWN_RIGHT: return direction != DOWN && direction != RIGHT;
            case TRAP: return level.isTrapOpen(position);
            default: return true;
        }
    }
    boolean canEnter(Direction direction, Tile tile, Level level){
        switch (tile) {
            case FLOOR: return true;
            case WALL: return false;
            case CHIP: return creatureType.isChip();
            case WATER: return true;
            case FIRE: return getCreatureType() != BUG && getCreatureType() != WALKER;
            case INVSIBLE_WALL: return false;
            case THIN_WALL_UP: return direction != DOWN;
            case THIN_WALL_RIGHT: return direction != LEFT;
            case THIN_WALL_DOWN: return direction != UP;
            case THIN_WALL_LEFT: return direction != RIGHT;
            case BLOCK: return false;
            case DIRT: return creatureType.isChip();
            case ICE:
            case FF_DOWN: return true;
            case BLOCK_UP:
            case BLOCK_LEFT:
            case BLOCK_RIGHT:
            case BLOCK_DOWN: return false;
            case FF_UP:
            case FF_LEFT:
            case FF_RIGHT: return true;
            case EXIT: return !creatureType.isMonster();
            case DOOR_BLUE: return creatureType.isChip() && level.keys[0] > 0;
            case DOOR_RED: return creatureType.isChip() && level.keys[1] > 0;
            case DOOR_GREEN: return creatureType.isChip() && level.keys[2] > 0;
            case DOOR_YELLOW: return creatureType.isChip() && level.keys[3] > 0;
            case ICE_SLIDE_SOUTHEAST: return direction == UP || direction == LEFT;
            case ICE_SLIDE_NORTHEAST: return direction == DOWN || direction == LEFT;
            case ICE_SLIDE_NORTHWEST: return direction == DOWN || direction == RIGHT;
            case ICE_SLIDE_SOUTHWEST: return direction == UP || direction == RIGHT;
            case BLUEWALL_FAKE: return creatureType.isChip();
            case BLUEWALL_REAL: return false;
            case OVERLAY_BUFFER: return false;
            case THIEF: return creatureType.isChip();
            case SOCKET: return creatureType.isChip() && level.chipsLeft <= 0;
            case BUTTON_GREEN: return true;
            case BUTTON_RED: return true;
            case TOGGLE_CLOSED: return false;
            case TOGGLE_OPEN: return true;
            case BUTTON_BROWN:
            case BUTTON_BLUE:
            case BOMB:
            case TRAP: return true;
            case HIDDENWALL_TEMP: return false;
            case GRAVEL: return (!creatureType.isMonster());
            case POP_UP_WALL: return creatureType.isChip();
            case HINT: return true;
            case THIN_WALL_DOWN_RIGHT: return direction == DOWN || direction == RIGHT;
            case CLONE_MACHINE: return false;
            case FF_RANDOM: return !creatureType.isMonster();
            case DROWNED_CHIP:
            case BURNED_CHIP:
            case BOMBED_CHIP:
            case UNUSED_36:
            case UNUSED_37:
            case ICEBLOCK_STATIC:
            case EXITED_CHIP:
            case EXIT_EXTRA_1:
            case EXIT_EXTRA_2: return false;
            case CHIP_SWIMMING_UP:
            case CHIP_SWIMMING_LEFT:
            case CHIP_SWIMMING_DOWN:
            case CHIP_SWIMMING_RIGHT: return !creatureType.isChip();
            //monsters
            default: return creatureType.isChip();
            case KEY_BLUE:
            case KEY_RED:
            case KEY_GREEN:
            case KEY_YELLOW: return true;
            case BOOTS_WATER:
            case BOOTS_FIRE:
            case BOOTS_ICE:
            case BOOTS_SLIDE: return !creatureType.isMonster();
            case CHIP_UP:
            case CHIP_LEFT:
            case CHIP_RIGHT:
            case CHIP_DOWN: return !creatureType.isChip();
        }
    }
    private boolean tryEnter(Direction direction, Level level, Position newPosition, Tile tile, List<Button> pressedButtons){
        sliding = false;
        switch (tile) {
            case FLOOR: return true;
            case WALL: return false;
            case CHIP:
                if (creatureType.isChip()) {
                    level.chipsLeft--;
                    level.layerFG.set(newPosition, FLOOR);
                    return true;
                }
                return false;
            case WATER:
                if (creatureType.isChip()){
                    if (level.boots[0] == 0){
                        level.layerFG.set(newPosition, DROWNED_CHIP);
                        kill();
                    }
                }
                else if (creatureType.isBlock()) {
                    level.layerFG.set(newPosition, DIRT);
                    kill();
                }
                else if (creatureType != GLIDER) kill();
                return true;
            case FIRE:
                if (creatureType.isChip()) {
                    if (level.boots[1] == 0){
                        level.layerFG.set(newPosition,  BURNED_CHIP);
                        kill();
                    }
                    return true;
                }
                switch (creatureType) {
                    case BLOCK:
                    case FIREBALL:
                        return true;
                    case BUG:
                    case WALKER:
                        return false;
                    default:
                        kill();
                        return true;
                }
            case INVSIBLE_WALL: return false;
            case THIN_WALL_UP: return direction != DOWN;
            case THIN_WALL_RIGHT: return direction != LEFT;
            case THIN_WALL_DOWN: return direction != UP;
            case THIN_WALL_LEFT: return direction != RIGHT;
            case BLOCK:
                if (creatureType.isChip()){
                    for (Creature m : level.slipList) {
                        if (m.position.equals(newPosition)) {
                            if (m.direction == direction || m.direction.turn(TURN_AROUND) == direction) return false;
                            if (m.tryMove(direction, level, false, pressedButtons)){
                                return tryEnter(direction, level, newPosition, level.layerFG.get(newPosition), pressedButtons);
                            }
                            return false;
                        }
                    }
                    Creature block = new Creature(newPosition, Tile.BLOCK);
                    if (block.tryMove(direction, level, false, pressedButtons)){
                        return tryEnter(direction, level, newPosition, level.layerFG.get(newPosition), pressedButtons);
                    }
                }
                return false;
            case DIRT:
                if (creatureType.isChip()) {
                    level.layerFG.set(newPosition, FLOOR);
                    return true;
                }
                return false;
            case ICE:
                if (!(creatureType.isChip() && level.getBoots()[2] > 0)) sliding = true;
                return true;
            case FF_DOWN:
                if (!(creatureType.isChip() && level.getBoots()[3] > 0)) sliding = true;
                return true;
            case BLOCK_UP:
            case BLOCK_LEFT:
            case BLOCK_RIGHT:
            case BLOCK_DOWN: return false;
            case FF_UP:
            case FF_LEFT:
            case FF_RIGHT:
                if (!(creatureType.isChip() && level.getBoots()[3] > 0)) sliding = true;
                return true;
            case EXIT:
                if (creatureType.isBlock()) return true;
                if (creatureType.isChip()){
                    level.layerFG.set(newPosition, EXITED_CHIP);
                    kill();
                    return true;
                }
                return false;
            case DOOR_BLUE:
                if (creatureType.isChip() && level.keys[0] > 0) {
                    level.keys[0] = (short) (level.keys[0] - 1);
                    level.layerFG.set(newPosition, FLOOR);
                    return true;
                }
                return false;
            case DOOR_RED:
                if (creatureType.isChip() && level.keys[1] > 0) {
                    level.keys[1] = (short) (level.keys[1] - 1);
                    level.layerFG.set(newPosition, FLOOR);
                    return true;
                }
                return false;
            case DOOR_GREEN:
                if (creatureType.isChip() && level.keys[2] > 0) {
                    level.layerFG.set(newPosition, FLOOR);
                    return true;
                }
                return false;
            case DOOR_YELLOW:
                if (creatureType.isChip() && level.keys[3] > 0) {
                    level.keys[3] = (short) (level.keys[3] - 1);
                    level.layerFG.set(newPosition, FLOOR);
                    return true;
                }
                return false;
            case ICE_SLIDE_SOUTHEAST:
                if (direction == UP || direction == LEFT){
                    if (!(creatureType.isChip() && level.getBoots()[2] > 0)) sliding = true;
                    return true;
                }
                else return false;
            case ICE_SLIDE_NORTHEAST:
                if(direction == DOWN || direction == LEFT){
                    if (!(creatureType.isChip() && level.getBoots()[2] > 0)) sliding = true;
                    return true;
                }
                else return false;
            case ICE_SLIDE_NORTHWEST:
                if(direction == DOWN || direction == RIGHT){
                    if (!(creatureType.isChip() && level.getBoots()[2] > 0)) sliding = true;
                    return true;
                }
                else return false;
            case ICE_SLIDE_SOUTHWEST:
                if(direction == UP || direction == RIGHT){
                    if (!(creatureType.isChip() && level.getBoots()[2] > 0)) sliding = true;
                    return true;
                }
                else return false;
            case BLUEWALL_FAKE:
                if (creatureType.isChip()) {
                    level.layerFG.set(newPosition, FLOOR);
                    return true;
                }
                else return false;
            case BLUEWALL_REAL:
                if (creatureType.isChip()) level.layerFG.set(newPosition, WALL);
                return false;
            case OVERLAY_BUFFER: return false;
            case THIEF:
                if (creatureType.isChip()) {
                    level.boots = new byte[]{0, 0, 0, 0};
                    return true;
                }
                return false;
            case SOCKET:
                if (creatureType.isChip() && level.chipsLeft <= 0) {
                    level.layerFG.set(newPosition, FLOOR);
                    return true;
                }
                return false;
            case BUTTON_GREEN:
                pressedButtons.add(level.getButton(newPosition, GreenButton.class));
                return true;
            case BUTTON_RED:
                Button b = level.getButton(newPosition, RedButton.class);
                if (b != null) pressedButtons.add(b);
                return true;
            case TOGGLE_CLOSED: return false;
            case TOGGLE_OPEN: return true;
            case BUTTON_BROWN:
                Button b2 = level.getButton(newPosition, BrownButton.class);
                if (b2 != null) pressedButtons.add(b2);
                return true;
            case BUTTON_BLUE:
                pressedButtons.add(level.getButton(newPosition, BlueButton.class));
                return true;
            case TELEPORT:
                sliding = true;
                teleport(direction, level, newPosition, pressedButtons);
                return true;
            case BOMB:
                if (!creatureType.isChip()) {
                    level.layerFG.set(newPosition, FLOOR);
                }
                kill();
                return true;
            case TRAP: return true;
            case HIDDENWALL_TEMP:
                if (creatureType.isChip()) {
                    level.layerFG.set(newPosition, WALL);
                }
                return false;
            case GRAVEL: return !creatureType.isMonster();
            case POP_UP_WALL:
                if (creatureType.isChip()) {
                    level.layerFG.set(newPosition, WALL);
                    return true;
                }
                return false;
            case HINT: return true;
            case THIN_WALL_DOWN_RIGHT: return direction == DOWN || direction == RIGHT;
            case CLONE_MACHINE: return false;
            case FF_RANDOM:
                if (creatureType.isMonster()) return false;
                if (!(creatureType.isChip() && level.getBoots()[3] > 0)) sliding = true;
                return true;
            case DROWNED_CHIP:
            case BURNED_CHIP:
            case BOMBED_CHIP:
            case UNUSED_36:
            case UNUSED_37:
            case ICEBLOCK_STATIC:
            case EXITED_CHIP:
            case EXIT_EXTRA_1:
            case EXIT_EXTRA_2: return false;
            case CHIP_SWIMMING_UP:
            case CHIP_SWIMMING_LEFT:
            case CHIP_SWIMMING_DOWN:
            case CHIP_SWIMMING_RIGHT:
                if (!creatureType.isChip()) {
                    level.chip.kill();
                    return true;
                }
                return false;
            default:                                    // Monsters
                if (creatureType.isChip()) {
                    kill();
                    return true;
                }
                return false;
            case KEY_BLUE:
                if (creatureType.isChip()) {
                    level.layerFG.set(newPosition, FLOOR);
                    level.keys[0]++;
                }
                return true;
            case KEY_RED:
                if (creatureType.isChip()) {
                    level.layerFG.set(newPosition, FLOOR);
                    level.keys[1]++;
                }
                return true;
            case KEY_GREEN:
                if (creatureType.isChip()) {
                    level.layerFG.set(newPosition, FLOOR);
                    level.keys[2]++;
                }
                return true;
            case KEY_YELLOW:
                if (creatureType.isChip()) {
                    level.layerFG.set(newPosition, FLOOR);
                    level.keys[3]++;
                }
                return true;
            case BOOTS_WATER:
                if (creatureType.isChip()) {
                    level.layerFG.set(newPosition, FLOOR);
                    level.boots[0] = 1;
                }
                return !creatureType.isMonster();
            case BOOTS_FIRE:
                if (creatureType.isChip()) {
                    level.layerFG.set(newPosition, FLOOR);
                    level.boots[1] = 1;
                }
                return !creatureType.isMonster();
            case BOOTS_ICE:
                if (creatureType.isChip()) {
                    level.layerFG.set(newPosition, FLOOR);
                    level.boots[2] = 1;
                }
                return !creatureType.isMonster();
            case BOOTS_SLIDE:
                if (creatureType.isChip()) {
                    level.layerFG.set(newPosition, FLOOR);
                    level.boots[3] = 1;
                }
                return !creatureType.isMonster();
            case CHIP_UP:
            case CHIP_LEFT:
            case CHIP_RIGHT:
            case CHIP_DOWN:
                if (!creatureType.isChip()) {
                    level.getChip().kill();
                    return true;
                }
                return false;
        }
    }
    private boolean tryMove(Direction direction, Level level, boolean slidingMove, List<Button> pressedButtons){
        if (direction == null) return false;
        Direction oldDirection = this.direction;
        boolean wasSliding = sliding;
        boolean isMonster = creatureType.isMonster();
        setDirection(direction);
        Position newPosition;
        if ((direction == LEFT && position.getX() == 0) ||
            (direction == RIGHT && position.getX() == 31) ||
            (direction == UP && position.getY() == 0) ||
            (direction == DOWN && position.getY() == 31)) newPosition = new Position(-1);
        else newPosition = position.move(direction);

        if (!canLeave(direction, level.layerBG.get(position), level)) return false;
        Tile newTile = level.layerFG.get(newPosition);
        if (!creatureType.isChip() && newTile.isChip()) newTile = level.layerBG.get(newPosition);
        if (!(newTile.isTransparent() && !canEnter(direction, level.layerBG.get(newPosition), level))) {
    
            if (tryEnter(direction, level, newPosition, newTile, pressedButtons)) {
                level.popTile(position);
                position = newPosition;
        
                if (sliding && !creatureType.isMonster())
                    this.direction = applySlidingTile(direction, level.layerFG.get(position), level.rng);
        
                if (!isDead()) level.insertTile(getPosition(), toTile());
                else if (isMonster) {
                    level.monsterList.numDeadMonsters++;
                }
        
                setSliding(wasSliding, sliding, level);
        
                return true;
            }
            
        }
        
        setSliding(wasSliding, sliding, level);
        
        if (wasSliding && !creatureType.isMonster()) {
            if (level.getLayerBG().get(this.position) == FF_RANDOM && !slidingMove) this.direction = oldDirection;
            else this.direction = applySlidingTile(direction, level.layerBG.get(position), level.rng);
        }
        
        return false;
    }

    boolean tick(Direction[] directions, Level level, boolean slidingMove){
        Creature oldCreature = clone();
        if (!creatureType.isChip() && !isSliding()) CreatureList.direction = direction;
        for (Direction newDirection : directions){
    
            LinkedList<Button> pressedButtons = new LinkedList<>();
            
            if (tryMove(newDirection, level, slidingMove, pressedButtons)){
                Iterator<Button> reverseIter = pressedButtons.descendingIterator();
                while (reverseIter.hasNext()) reverseIter.next().press(level);
                if (level.getLayerFG().get(oldCreature.position) == BUTTON_BROWN){
                    BrownButton b = ((BrownButton) level.getButton(oldCreature.position, BrownButton.class));
                    if (b != null && level.getLayerBG().get(b.getTargetPosition()) != TRAP && !b.getTargetPosition().equals(position)) {
                        b.release(level);
                    }
                }
                if (level.getLayerFG().get(oldCreature.position) == TRAP){
                    for (BrownButton b : level.getBrownButtons()) {
                        if (b.getTargetPosition().equals(oldCreature.position) && level.getLayerFG().get(b.getButtonPosition()) == BUTTON_BROWN) {
                            b.release(level);
                        }
                    }
                }
                if (!creatureType.isChip()) {
                    if (level.getLayerBG().get(position).isChip()) level.getChip().kill();
                    if (!isSliding()) CreatureList.direction = newDirection;
                }
                return true;
            }
            if (!creatureType.isChip() && !isSliding()) CreatureList.direction = newDirection;
            
        }
        setSliding(oldCreature.sliding, level);
        if (creatureType.isTank() && !isSliding()) setCreatureType(TANK_STATIONARY);
        if (!creatureType.isChip() &&!(creatureType.isBlock() && level.layerBG.get(position) == FF_RANDOM)) setDirection(oldCreature.direction);
        else level.getLayerFG().set(position, toTile());
        return false;
    }
    
    public Creature(Direction direction, CreatureID creatureType, Position position){
        this.direction = direction;
        this.creatureType = creatureType;
        this.position = position;
    }
    public Creature(Position position, Tile tile){
        this.position = position;
        if (BLOCK_UP.ordinal() <= tile.ordinal() && tile.ordinal() <= BLOCK_RIGHT.ordinal()){
            direction = Direction.fromOrdinal((tile.ordinal() + 2) % 4);
            creatureType = BLOCK;
        }
        else{
            direction = Direction.fromOrdinal(tile.ordinal() % 4);
            if (tile == Tile.BLOCK) creatureType = BLOCK;
            else creatureType = CreatureID.fromOrdinal((tile.ordinal() - 0x40) >>> 2);
        }
        if (creatureType == TANK_STATIONARY) creatureType = TANK_MOVING;
    }
    public Creature(int bitMonster){
        direction = Direction.fromOrdinal(bitMonster >>> 14);
        creatureType = CreatureID.fromOrdinal((bitMonster >>> 10) & 0b1111);
        if (creatureType == CHIP_SLIDING) sliding = true;
        position = new Position(bitMonster & 0b00_0000_1111111111);
    }

    public int bits(){
        return direction.getBits() | creatureType.getBits() | position.getIndex();
    }

    @Override
    public Creature clone(){
        Creature c = new Creature(direction, creatureType, position);
        c.sliding = sliding;
        return c;
    }

    @Override
    public String toString(){
        if (creatureType == DEAD) return "Dead monster at position " + position;
        return creatureType+" facing "+direction+" at position "+position;
    }

}

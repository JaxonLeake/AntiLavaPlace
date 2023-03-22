package me.modernadventurer.waterbreather;

import org.bukkit.event.HandlerList;
import org.bukkit.event.server.ServerEvent;

public final class GameTickEvent extends ServerEvent {
	
	private static final HandlerList handlers = new HandlerList();
	int gameTick;

	public GameTickEvent(int gameTick) {
		this.gameTick = gameTick;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
	
	@Override
	public final HandlerList getHandlers() {
		return handlers;
	}
	
	public final Integer getGameTick() {
		return gameTick;
	}
}
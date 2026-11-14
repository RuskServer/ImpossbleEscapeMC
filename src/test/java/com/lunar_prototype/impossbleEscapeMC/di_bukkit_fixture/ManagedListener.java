package com.lunar_prototype.impossbleEscapeMC.di_bukkit_fixture;

import com.lunar_prototype.impossbleEscapeMC.core.di.annotations.Component;
import com.lunar_prototype.impossbleEscapeMC.core.lifecycle.*;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerToggleSneakEvent;

@Component
public class ManagedListener implements Listener, Startable, Stoppable {
    public int starts, stops, events;
    public void start() { starts++; }
    public void stop() { stops++; }
    @EventHandler public void onSneak(PlayerToggleSneakEvent event) { events++; }
}

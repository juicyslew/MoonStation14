package com.juicyslew.moonstation14.ms14.atmos.teg.fixture;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Advances only explicitly armed disposable continuous fixtures, using their ordinary guarded actions. */
@EventBusSubscriber(modid="moonstation14")
public final class TegAutomaticStartupController {
    private TegAutomaticStartupController() { }

    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if(event.getLevel() instanceof ServerLevel level) advance(level);
    }

    static void advance(ServerLevel level) {
        var data=TegFixtureSavedData.existing(level).orElse(null);
        if(data==null)return;
        for(var fixture:data.entries()) {
            var armed=data.automaticStartup(fixture.origin()).orElse(null);
            if(armed==null)continue;
            if(armed.status()==TegFixtureSavedData.AutomaticStartupStatus.COMPLETE||armed.status()==TegFixtureSavedData.AutomaticStartupStatus.FAILED)continue;
            try {
            var entry=data.entry(armed.origin()).orElse(null);
            if(entry==null){fail(level,armed,"saved fixture disappeared");continue;}
            var decision=TegAutomaticStartupPolicy.decide(armed.status(),entry.phase(),entry.initialized(),level.getGameTime(),armed.armTick(),entry.phaseStartedTick());
            if(decision.attempt()==TegAutomaticStartupPolicy.Attempt.WAIT)continue;
            if(decision.attempt()==TegAutomaticStartupPolicy.Attempt.FAIL){fail(level,armed,decision.failureReason());continue;}
            String result;
            switch(decision.attempt()) {
                case START -> {
                    result=TegContinuousFixtureService.initializeOperating(level,armed.origin(),TegContinuousOperatingPlan.Stage.COLD_PREPARATION);
                    if(TegAutomaticStartupPolicy.retryablePowerWait(result)) continue;
                    if(!result.startsWith("Operating startup admitted once:")){fail(level,armed,result);continue;}
                    data.updateAutomaticStartup(armed.origin(),TegFixtureSavedData.AutomaticStartupStatus.WAITING_START,TegFixtureSavedData.AutomaticStartupStatus.PREPARING_COLD,"");
                }
                case START_HEATING -> { result=TegContinuousFixtureService.startHeatingAfterColdPreparation(level,armed.origin()); if(!result.startsWith("Operating heating controls confirmed:")){if(TegAutomaticStartupPolicy.retryablePowerWait(result))continue;fail(level,armed,result);continue;} data.updateAutomaticStartup(armed.origin(),TegFixtureSavedData.AutomaticStartupStatus.PREPARING_COLD,TegFixtureSavedData.AutomaticStartupStatus.ACTIVE,""); }
                case INITIALIZE -> { result=TegContinuousFixtureService.initializeOperating(level,armed.origin()); if(!result.startsWith("Operating startup admitted once:")){if(TegAutomaticStartupPolicy.retryablePowerWait(result))continue;fail(level,armed,result);continue;} }
                case IGNITE -> { result=TegContinuousFixtureService.ignite(level,armed.origin()); if(!transitioned(level,armed.origin(),TegFixtureSavedData.ContinuousPhase.IGNITION)){if(result.startsWith("Ignition not ready:"))continue;fail(level,armed,result);continue;} }
                case RUN -> { result=TegContinuousFixtureService.run(level,armed.origin()); if(!transitioned(level,armed.origin(),TegFixtureSavedData.ContinuousPhase.RUNNING)){if(result.startsWith("RUNNING not ready:"))continue;fail(level,armed,result);continue;} }
                  case HEATER_OFF -> { result=TegContinuousFixtureService.heaterOff(level,armed.origin()); if(!transitioned(level,armed.origin(),TegFixtureSavedData.ContinuousPhase.HEATER_OFF)){if(result.startsWith("Heater-off not ready:"))continue;fail(level,armed,result);continue;} notify(level,armed,"Finishing startup: waiting for fresh heater-off power acknowledgement.",false); continue; }
                 case COMPLETE -> { var current=data.entry(armed.origin()).orElse(null); if(current==null){fail(level,armed,"saved fixture disappeared");continue;} if(TegContinuousFixtureService.heaterOffAcknowledged(level,current)){data.updateAutomaticStartup(armed.origin(),TegFixtureSavedData.AutomaticStartupStatus.ACTIVE,TegFixtureSavedData.AutomaticStartupStatus.COMPLETE,"");notify(level,armed,"TEG startup complete.",false);continue;} if(level.getGameTime()-current.phaseStartedTick()>=200){fail(level,armed,"fresh heater-off power acknowledgement deadline (200 ticks) expired");} continue; }
                default -> { continue; }
            }
             notify(level,armed,automaticPhaseMessage(level,armed.origin()),false);
            } catch(RuntimeException failure) {
                fail(level,armed,"automatic startup action failed: "+(failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage()));
            }
        }
        for(var fixture:data.entries()) { var armed=data.automaticStartup(fixture.origin()).orElse(null); if(armed!=null&&(armed.status()==TegFixtureSavedData.AutomaticStartupStatus.ACTIVE||armed.status()==TegFixtureSavedData.AutomaticStartupStatus.WAITING_START||armed.status()==TegFixtureSavedData.AutomaticStartupStatus.PREPARING_COLD) && level.getGameTime()%20==0) {
             try { String progress=armed.status()==TegFixtureSavedData.AutomaticStartupStatus.ACTIVE?TegContinuousFixtureService.automaticProgress(level,armed.origin()):armed.status()==TegFixtureSavedData.AutomaticStartupStatus.PREPARING_COLD?"Cold prep "+Math.max(0,10-(int)((level.getGameTime()-TegFixtureSavedData.existing(level).flatMap(d->d.entry(armed.origin())).map(TegFixtureSavedData.Entry::phaseStartedTick).orElse(level.getGameTime()))/20))+"s":"Heating"; notify(level,armed,progress,true); }
            catch(RuntimeException failure) { fail(level,armed,"owned hardware validation failed: "+failure.getMessage()); }
        } }
    }

    static String automaticPhaseMessage(ServerLevel level,BlockPos origin) {
        var phase=TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).map(TegFixtureSavedData.Entry::phase).orElse(null);
        return "TEG phase: "+(phase==null?"unavailable":phase.name());
    }

    private static boolean transitioned(ServerLevel level,BlockPos origin,TegFixtureSavedData.ContinuousPhase phase) {
        return TegFixtureSavedData.existing(level).flatMap(d->d.entry(origin)).map(e->e.phase()==phase).orElse(false);
    }
    private static void fail(ServerLevel level,TegFixtureSavedData.AutomaticStartup armed,String reason) {
        try { if(TegFixtureSavedData.existing(level).flatMap(d->d.entry(armed.origin())).isPresent())
                com.juicyslew.moonstation14.MoonStation14.LOGGER.error("Automatic TEG failure operating snapshot origin={} reason={} status={}",armed.origin(),reason,TegContinuousFixtureService.operatingStatus(level,armed.origin())); }
        catch(RuntimeException snapshotFailure) { com.juicyslew.moonstation14.MoonStation14.LOGGER.error("Automatic TEG failure operating snapshot unavailable origin={} reason={}",armed.origin(),reason,snapshotFailure); }
        try { var shutdown=TegFixtureSavedData.existing(level).flatMap(d->d.entry(armed.origin())).map(entry->TegContinuousFixtureService.quiesceOwned(level,entry)).orElse(null);
            if(shutdown==null) reason += "; safe shutdown incomplete: saved ownership unavailable";
            else if(!shutdown.complete()) reason += "; safe shutdown incomplete: "+bounded(shutdown.summary());
        } catch(RuntimeException quiesceFailure) { reason += "; safe shutdown incomplete: "+bounded(quiesceFailure.getMessage()==null?quiesceFailure.getClass().getSimpleName():quiesceFailure.getMessage()); }
        String failureReason=reason;
        boolean changed=TegFixtureSavedData.existing(level).map(d->d.updateAutomaticStartup(armed.origin(),armed.status(),TegFixtureSavedData.AutomaticStartupStatus.FAILED,failureReason)).orElse(false);
        if(changed) {
            com.juicyslew.moonstation14.MoonStation14.LOGGER.error("Automatic TEG startup failed origin={} reason={}",armed.origin(),failureReason);
            notify(level,armed,"TEG startup failed: "+bounded(failureReason,80),false);
        }
    }
    private static String bounded(String value) { return value.length()<=240?value:value.substring(0,237)+"..."; }
    private static String bounded(String value,int max) { return value.length()<=max?value:value.substring(0,max-3)+"..."; }
    private static void notify(ServerLevel level,TegFixtureSavedData.AutomaticStartup armed,String text,boolean actionbar) {
        var player=level.getServer().getPlayerList().getPlayer(armed.initiator());
        if(player!=null&&player.serverLevel()==level)player.displayClientMessage(Component.literal(text),actionbar);
    }
}

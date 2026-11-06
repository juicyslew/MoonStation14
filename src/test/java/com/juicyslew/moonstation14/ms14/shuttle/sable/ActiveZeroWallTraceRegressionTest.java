package com.juicyslew.moonstation14.ms14.shuttle.sable;

import com.juicyslew.moonstation14.ms14.movement.MovementVector;
import com.juicyslew.moonstation14.ms14.movement.NativeCollisionSlop;
import com.juicyslew.moonstation14.ms14.movement.HorizontalContactEvidence;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Frozen native first-side tick from artifact_task_1e5a3b5484662b423512c3d6_log_8ef4a15411eabce55751 (SHA-256 664b42d222f701d5ca7cc5e04d8a9b8bd0e283ae4766d1fb09777ccb24233a67). */
class ActiveZeroWallTraceRegressionTest {
    private static final UUID FRAME = UUID.fromString("bb266dd3-b5ed-4420-9835-eece966a2a0f");
    private static final BlockPos POS = new BlockPos(20481037,130,20481032);
    private static final AABB BOUNDS = new AABB(20481037,130,20481032,20481038,131,20481033);
    private static final MovementVector DIMS = new MovementVector(.6000000238418579,1.7999999523162842,.6000000238418579);
    private static final MovementVector REQUEST = new MovementVector(0,0,.15625);
    private static final MovementVector NATIVE = new MovementVector(7.415803173084957e-7,9.893394376384722e-6,.0010200082759098065);
    private static final MovementVector MEASURED = new MovementVector(7.415801519528031e-7,9.893394349091977e-6,.0010200082761002705);

    // Matrices and orientations are the captured per-substep snapshots; repeated iterations reuse them exactly.
    private static final double[][] POSES = {
        {.9999999999900383,-1.0136441203409503e-6,4.346926758116169e-6,0,1.0133977425151182e-6,.9999999983932742,5.667825132306207e-5,0,-4.3469842027080545e-6,-5.66782469173317e-5,.9999999983843398,0,-2.04782104690879e7,1353.5949915468361,-2.0478628503896344e7,1},
        {.9999999999895766,-1.0568692783289827e-6,4.441924251061525e-6,0,1.0566105970207904e-6,.9999999983037312,5.8235915754199787e-5,0,-4.441985791277082e-6,-5.823591106020851e-5,.9999999982944241,0,-2.0478208523352947e7,1386.3830192276534,-2.04786304479067e7,1},
        {.9999999999891036,-1.1000945842891778e-6,4.5369216766721e-6,0,1.099823303543842e-6,.9999999982117594,5.979358018923027e-5,0,-4.536987447152733e-6,-5.9793575198766514e-5,.9999999982020725,0,-2.0478206577616382e7,1419.1710498509597,-2.0478632391865794e7,1},
        {.9999999999886192,-1.1433200382213782e-6,4.631919034947551e-6,0,1.1430358620841152e-6,.9999999981173593,6.135124462814782e-5,0,-4.631989170334665e-6,-6.135123933300001e-5,.9999999981072851,0,-2.0478204631878205e7,1451.959083416635,-2.0478634335773613e7,1},
        {.9999999999881245,-1.1865456401254263e-6,4.72691632588753e-6,0,1.1862482726414523e-6,.9999999980205313,6.290890907094679e-5,0,-4.726990960822528e-6,-6.290890346290338e-5,.999999998010063,0,-2.047820268613844e7,1484.7471199245608,-2.0478636279630195e7,1},
        {.9999999999876183,-1.2297713900011628e-6,4.821913549491688e-6,0,1.2294605352156948e-6,.9999999979212747,6.446657351762147e-5,0,-4.821992818615977e-6,-6.446656758847085e-5,.999999997910405,0,-2.0478200740397066e7,1517.535159374616,-2.0478638223435506e7,1},
        {.999999999987102,-1.2729972878484315e-6,4.916910705759681e-6,0,1.2726726498066855e-6,.9999999978195903,6.602423796816617e-5,0,-4.916994743714666e-6,-6.602423170969679e-5,.9999999978083122,0,-2.04781987946541e7,1550.3232017666812,-2.047864016718957e7,1},
        {.9999999999865746,-1.3162233336670732e-6,5.01190779469116e-6,0,1.3158846164142665e-6,.9999999977154778,6.75819024225752e-5,0,-5.0119967361182454e-6,-6.758189582657549e-5,.999999997703784,0,-2.0478196848909535e7,1583.111247100637,-2.0478642110892378e7,1},
        {.999999999986036,-1.3594495274569299e-6,5.106904816285777e-6,0,1.359096435038279e-6,.9999999976089368,6.913956688084285e-5,0,-5.106998795826369e-6,-6.913955993910123e-5,.99999999759682,0,-2.0478194903163355e7,1615.899295376363,-2.0478644054543916e7,1},
        {.9999999999854864,-1.4026758692178435e-6,5.201901770543185e-6,0,1.4023081056785655e-6,.9999999974999675,7.069723134296339e-5,0,-5.202000922838686e-6,-7.06972240472683e-5,.9999999974874209,0,-2.0478192957415573e7,1648.6873465937388,-2.0478645998144202e7,1}
    };
    private static final double[][] Q = {{0,1},{-2.2209775124738164e-6,.9999999999975336},{-2.268477282990336e-6,.999999999997427},{-2.315977053506841e-6,.9999999999973181},{-2.3634768240233295e-6,.999999999997207},{-2.4109765945398025e-6,.9999999999970935},{-2.458476365056259e-6,.999999999996978},{-2.505976135572699e-6,.9999999999968601},{-2.553475906089122e-6,.9999999999967398},{-2.6009756766055278e-6,.9999999999966175}};

    private record Raw(int step,int iteration, MovementVector selected, MovementVector before,
                       MovementVector response, MovementVector center) { }
    private static MovementVector v(double x,double y,double z){return new MovementVector(x,y,z);}
    private static final Raw[] RAW = {
        new Raw(1,0,v(6.349333393257598e-8,8.278591985665026e-7,-.014606295047135827),v(0,0,.015625),v(6.349333393257598e-8,8.278591985665026e-7,-.014606295047135827),v(2738.0001236750086,302.90698114382513,2492.214556447697)),
        new Raw(1,1,v(1.9767795505267965e-18,2.57742826385455e-17,-4.5474735015174704e-13),v(6.349333393257598e-8,8.278591985665026e-7,.0010187049528641735),v(1.9767763755445243e-18,2.5774259672882698e-17,-4.547473508864641e-13),v(2738.0001237385018,302.90698197168433,2492.1999501526498)),
        new Raw(2,0,v(6.940024248560607e-8,9.098602604459689e-7,-.01562369751463927),v(6.349333393455276e-8,8.278591985922768e-7,.016643704952409424),v(6.940024248560607e-8,9.098602604459689e-7,-.01562369751463927),v(2738.0001243071893,302.9071448725399,2492.2155729424485)),
        new Raw(3,0,v(7.0890428597674e-8,9.342746090002822e-7,-.015624999913699715),v(1.3289357642015883e-7,1.7377194590382457e-6,.016645007437770157),v(7.0890428597674e-8,9.342746090002821e-7,-.015624999913699715),v(2738.000124944189,302.90730868325926,2492.2155720330693)),
        new Raw(3,1,v(2.0631830225978464e-18,2.7190969921669642e-17,-4.5474735006886116e-13),v(2.0378400501783283e-7,2.671994068038528e-6,.0010200075240704414),v(2.0631869710542637e-18,2.7191028156283922e-17,-4.547473508864641e-13),v(2738.0001250150794,302.9073096175339,2492.1999470331557)),
        new Raw(4,0,v(7.237483049579803e-8,9.586131107280199e-7,-.015624999907671196),v(2.0378400501989602e-7,2.671994068065719e-6,.016645007523615696),v(7.237483049579803e-8,9.586131107280197e-7,-.015624999907671196),v(2738.00012558268,302.9074725183964,2492.215569821307)),
        new Raw(5,0,v(7.385923345761069e-8,9.82951612545578e-7,-.015624999904333107),v(2.7615883551569406e-7,3.6306071787937385e-6,.0166450076159445),v(7.385923345761069e-8,9.829516125455781e-7,-.015624999904333107),v(2738.0001262226556,302.9076363778754,2492.215567609567)),
        new Raw(5,1,v(2.149586617098306e-18,2.8607657196927616e-17,-4.5474734998154544e-13),v(3.5001806897330474e-7,4.613558791339317e-6,.001020007711611393),v(2.149610801453804e-18,2.860769076056674e-17,-4.547473508864641e-13),v(2738.0001262965147,302.90763736082704,2492.199942609663)),
        new Raw(6,0,v(7.53436374597636e-8,1.0072901141431412e-6,-.01562499989868323),v(3.5001806897545435e-7,4.613558791367924e-6,.016645007711156644),v(7.53436374597636e-8,1.0072901141431412e-6,-.01562499989868323),v(2738.0001268641167,302.9078002616964,2492.215565397847)),
        new Raw(7,0,v(7.682804250607522e-8,1.031628615570044e-6,-.015624999891631056),v(4.2536170643521796e-7,5.620848905511065e-6,.01664500781247341),v(7.682804250607526e-8,1.0316286155700444e-6,-.015624999891631056),v(2738.000127507062,302.9079641698593,2492.2155631861483)),
        new Raw(7,1,v(4.471980668053825e-18,6.004868892859738e-17,-9.094946997795996e-13),v(5.021897489412932e-7,6.65247752108111e-6,.0010200079208423554),v(4.4720163241474854e-18,6.004870672969911e-17,-9.094947017729282e-13),v(2738.0001275838904,302.9079652014879,2492.199938186257)),
        new Raw(8,0,v(7.831244861210569e-8,1.0559671170349521e-6,-.015624999886359825),v(5.021897489457652e-7,6.652477521141158e-6,.016645007919932862),v(7.831244861210572e-8,1.0559671170349526e-6,-.015624999886359825),v(2738.0001281514933,302.9081281023641,2492.2155609744723)),
        new Raw(8,1,v(2.279192238401376e-18,3.0732688095020184e-17,-4.547473498422659e-13),v(5.80502197557871e-7,7.708444638176111e-6,.0010200080335730378),v(2.2791539028245086e-18,3.0732049392280525e-17,-4.547473508864641e-13),v(2738.000128229806,302.90812915833123,2492.199935974586)),
        new Raw(9,0,v(7.979685577372325e-8,1.0803056184833942e-6,-.015624999881960032),v(5.805021975601501e-7,7.708444638206843e-6,.01664500803311829),v(7.979685577372329e-8,1.0803056184833944e-6,-.015624999881960032),v(2738.0001287974096,302.90829205921085,2492.215558762818)),
        new Raw(10,0,v(8.12812639722568e-8,1.1046441196623353e-6,-.015624999874793702),v(6.602990533338734e-7,8.788750256690237e-6,.016645008151158255),v(8.128126397225679e-8,1.104644119662335e-6,-.015624999874793702),v(2738.0001294448107,302.9084560403995,2492.2155565511844)),
        new Raw(10,1,v(2.3655961389698343e-18,3.214937535052209e-17,-4.547473497438754e-13),v(7.415803173061302e-7,9.893394376352572e-6,.0010200082763645538),v(2.3655512634444473e-18,3.2149982545984224e-17,-4.547473508864641e-13),v(2738.0001295260918,302.9084571450436,2492.19993155131))
    };

    private static List<CurrentFrameCollisionScope.NativeCollisionTrace> rows() {
        var rows = new ArrayList<CurrentFrameCollisionScope.NativeCollisionTrace>();
        for (var r : RAW) {
            var pose = new NativeFrameInterval.PoseValue(POSES[r.step()-1]);
            var orientation = new NativeCollisionGeometry.QuaternionValue(0,Q[r.step()-1][0],0,Q[r.step()-1][1]);
            var geometry = new NativeCollisionGeometry(r.center(),DIMS,orientation,pose,BOUNDS,r.selected(),r.before(),r.iteration(),10);
            rows.add(new CurrentFrameCollisionScope.NativeCollisionTrace(FRAME,r.step(),10,r.iteration(),POS,
                    Blocks.IRON_BLOCK.defaultBlockState(),"captured:steel_wall","captured:steel_wall",
                    r.selected().x(),r.selected().y(),r.selected().z(),r.before().x(),r.before().y(),r.before().z(),
                    r.selected().x(),r.selected().y(),r.selected().z(),r.response().x(),r.response().y(),r.response().z(),geometry,false));
        }
        return List.copyOf(rows);
    }

    @Test void allSixteenFrozenRowsMatchNativeSatWitness() {
        var trace = rows();
        assertEquals(16, trace.size());
        var witness = new NativeSideCollisionWitness();
        double rx=0,ry=0,rz=0;
        for (int i=0;i<trace.size();i++) {
            var row=trace.get(i);
            var selected=new MovementVector(row.selectedX(),row.selectedY(),row.selectedZ());
            assertEquals(selected,new MovementVector(row.appliedX(),row.appliedY(),row.appliedZ()),"applied row "+i);
            assertEquals(selected,row.geometry().maxMtv(),"max MTV row "+i);
            assertEquals(row.collisionMotionBeforeX(),row.geometry().collisionMotion().x(),0);
            assertEquals(row.collisionMotionBeforeY(),row.geometry().collisionMotion().y(),0);
            assertEquals(row.collisionMotionBeforeZ(),row.geometry().collisionMotion().z(),0);
            var replay=witness.separation(row.geometry());
            assertNotNull(replay,"SAT row "+i+" step "+row.substep()+" iteration "+row.iteration());
            assertEquals(selected.x(),replay.x(),NativeCollisionSlop.MAX_RESIDUAL,"SAT x row "+i);
            assertEquals(selected.y(),replay.y(),NativeCollisionSlop.MAX_RESIDUAL,"SAT y row "+i);
            assertEquals(selected.z(),replay.z(),NativeCollisionSlop.MAX_RESIDUAL,"SAT z row "+i);
            rx+=row.responseX();ry+=row.responseY();rz+=row.responseZ();
        }
        assertEquals(NATIVE.x(),REQUEST.x()+rx,2e-12);
        assertEquals(NATIVE.y(),REQUEST.y()+ry,2e-12);
        assertEquals(NATIVE.z(),REQUEST.z()+rz,2e-12);
        assertEquals(9.893394376384722e-6,ry,2e-12,"positive side-response Y sum explains native Y");
        MovementVector prior=MovementVector.ZERO;
        for (var row:trace) {
            assertEquals(0,row.substep()==0?1:0);
            assertEquals("captured:steel_wall",row.blockState());
            var expected=new MovementVector(REQUEST.x()*row.substep()/10d+prior.x(),REQUEST.y()*row.substep()/10d+prior.y(),REQUEST.z()*row.substep()/10d+prior.z());
            assertEquals(expected.x(),row.collisionMotionBeforeX(),2e-12);
            assertEquals(expected.y(),row.collisionMotionBeforeY(),2e-12);
            assertEquals(expected.z(),row.collisionMotionBeforeZ(),2e-12);
            prior=prior.add(new MovementVector(row.responseX(),row.responseY(),row.responseZ()));
        }
    }

    @Test void boundedOwnAndMutationDiagnostics() {
        var trace=rows();
        var known=(java.util.function.Predicate<CurrentFrameCollisionScope.NativeCollisionTrace>) r -> r.selectedState()==Blocks.IRON_BLOCK.defaultBlockState()
                && r.block().equals("captured:steel_wall") && r.blockState().equals("captured:steel_wall");
        var proof=NativeSideContactProof.validateDetailed(REQUEST,NATIVE,MEASURED,FRAME,trace,known);
        assertEquals(NativeSideContactProof.Failure.NONE,proof.reason());
        var result=proof.result().orElseThrow();
        assertEquals(REQUEST.y(),result.boundedOwn().y(),0d);
        assertEquals(MEASURED.y()-REQUEST.y(),result.geometryCorrection().y(),0d);
        assertEquals(MEASURED.x()-result.boundedOwn().x(),result.geometryCorrection().x(),0d);
        assertEquals(MEASURED.z()-result.boundedOwn().z(),result.geometryCorrection().z(),0d);
        var firstNormal=new MovementVector(4.346984202708055e-6,5.6678246917331716e-5,-.9999999983843401);
        var cert=HorizontalContactEvidence.validate(REQUEST,new MovementVector(0,MEASURED.y(),MEASURED.z()),firstNormal.x(),firstNormal.y(),firstNormal.z());
        assertTrue(cert.isPresent());
        assertTrue(result.horizontalContact()!=null);
        assertTrue(SableOwnedBodyMotion.outerPositionProofAdmissible(cert.orElseThrow(),false,true,false,false,null,null));
        assertTrue(com.juicyslew.moonstation14.ms14.movement.ContactPositionCorrectionEvidence
                .validateAirborneSideGeometry(REQUEST,result.boundedOwn(),result.geometryCorrection(),result.horizontalContact()).isPresent());
        assertTrue(com.juicyslew.moonstation14.ms14.movement.ContactPositionCorrectionEvidence
                .validateSide(REQUEST,result.boundedOwn(),result.geometryCorrection(),result.horizontalContact()).isEmpty());
        assertFalse(com.juicyslew.moonstation14.ms14.movement.ContactPositionCorrectionEvidence
                .validateAirborneSideGeometry(REQUEST,result.boundedOwn(),result.geometryCorrection(),result.horizontalContact())
                .orElseThrow().matches(REQUEST,result.boundedOwn(),true));
        var selected=trace.stream().map(r->new MovementVector(r.selectedX(),r.selectedY(),r.selectedZ())).toList();
        var candidate=NativeSideContactProof.ownResponsesAt(REQUEST,10,trace,selected);
        var accepted=NativeCollisionSlop.accepted(REQUEST,REQUEST.add(candidate));
        assertEquals(result.boundedOwn(),accepted);
        assertEquals(REQUEST.y(),accepted.y(),0d);
        assertEquals(MEASURED.y()-accepted.y(),result.geometryCorrection().y(),0d,"signed geometric remainder Y");
        assertTrue(HorizontalContactEvidence.validate(REQUEST,new MovementVector(0,accepted.y(),accepted.z()),firstNormal.x(),firstNormal.y(),firstNormal.z()).isPresent());
        var wrongFrame=new ArrayList<>(trace);
        var r=trace.get(0);
        wrongFrame.set(0,new CurrentFrameCollisionScope.NativeCollisionTrace(UUID.randomUUID(),r.substep(),r.substeps(),r.iteration(),r.selectedPos(),r.selectedState(),r.block(),r.blockState(),r.selectedX(),r.selectedY(),r.selectedZ(),r.collisionMotionBeforeX(),r.collisionMotionBeforeY(),r.collisionMotionBeforeZ(),r.appliedX(),r.appliedY(),r.appliedZ(),r.responseX(),r.responseY(),r.responseZ(),r.geometry(),false));
        assertEquals(NativeSideContactProof.Failure.UNKNOWN_DATA_OR_FRAME,NativeSideContactProof.validateDetailed(REQUEST,NATIVE,MEASURED,FRAME,wrongFrame,known).reason());
        assertEquals(NativeSideContactProof.Failure.UNKNOWN_DATA_OR_FRAME,NativeSideContactProof.validateDetailed(REQUEST,NATIVE,MEASURED,FRAME,trace,t->false).reason());
        var changed=new ArrayList<>(trace); changed.set(0,trace.get(1));
        assertEquals(NativeSideContactProof.Failure.INVALID_ORDER,NativeSideContactProof.validateDetailed(REQUEST,NATIVE,MEASURED,FRAME,changed,known).reason());
        var responseChanged=new ArrayList<>(trace);
        var first=responseChanged.getFirst();
        responseChanged.set(0,new CurrentFrameCollisionScope.NativeCollisionTrace(first.frameId(),first.substep(),first.substeps(),first.iteration(),first.selectedPos(),first.selectedState(),first.block(),first.blockState(),first.selectedX(),first.selectedY(),first.selectedZ(),first.collisionMotionBeforeX(),first.collisionMotionBeforeY(),first.collisionMotionBeforeZ(),first.appliedX(),first.appliedY(),first.appliedZ(),first.responseX(),first.responseY()+1e-5,first.responseZ(),first.geometry(),first.verticalCollision()));
        assertEquals(NativeSideContactProof.Failure.GEOMETRY_REPLAY_FAILURE,NativeSideContactProof.validateDetailed(REQUEST,NATIVE,MEASURED,FRAME,responseChanged,known).reason());
        assertEquals(NativeSideContactProof.Failure.UNEXPLAINED_Y,NativeSideContactProof.validateDetailed(REQUEST,NATIVE,new MovementVector(MEASURED.x(),MEASURED.y()+1e-4,MEASURED.z()),FRAME,trace,known).reason());
        assertEquals(NativeSideContactProof.Failure.REQUEST_ACCUMULATION_MISMATCH,NativeSideContactProof.validateDetailed(new MovementVector(0,1e-4,REQUEST.z()),NATIVE,MEASURED,FRAME,trace,known).reason());
        assertEquals(NativeSideContactProof.Failure.FINAL_TRACE_RECONSTRUCTION_MISMATCH,NativeSideContactProof.validateDetailed(REQUEST,new MovementVector(NATIVE.x(),NATIVE.y()+1e-4,NATIVE.z()),MEASURED,FRAME,trace,known).reason());
        var noGeometry=new ArrayList<>(trace);
        noGeometry.set(0,new CurrentFrameCollisionScope.NativeCollisionTrace(r.frameId(),r.substep(),r.substeps(),r.iteration(),r.selectedPos(),r.selectedState(),r.block(),r.blockState(),r.selectedX(),r.selectedY(),r.selectedZ(),r.collisionMotionBeforeX(),r.collisionMotionBeforeY(),r.collisionMotionBeforeZ(),r.appliedX(),r.appliedY(),r.appliedZ(),r.responseX(),r.responseY(),r.responseZ(),null,false));
        assertEquals(NativeSideContactProof.Failure.GEOMETRY_REPLAY_FAILURE,NativeSideContactProof.validateDetailed(REQUEST,NATIVE,MEASURED,FRAME,noGeometry,known).reason());
        var mixed=new ArrayList<>(trace);
        mixed.set(0,new CurrentFrameCollisionScope.NativeCollisionTrace(r.frameId(),r.substep(),r.substeps(),r.iteration(),r.selectedPos(),r.selectedState(),r.block(),r.blockState(),r.selectedX(),r.selectedY(),r.selectedZ(),r.collisionMotionBeforeX(),r.collisionMotionBeforeY(),r.collisionMotionBeforeZ(),r.appliedX(),r.appliedY(),r.appliedZ(),r.responseX(),r.responseY(),r.responseZ(),r.geometry(),true));
        assertNotEquals(NativeSideContactProof.Failure.NONE,NativeSideContactProof.validateDetailed(REQUEST,NATIVE,MEASURED,FRAME,mixed,known).reason());
    }
}

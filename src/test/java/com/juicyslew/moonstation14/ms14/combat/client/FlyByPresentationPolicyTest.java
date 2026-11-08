package com.juicyslew.moonstation14.ms14.combat.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FlyByPresentationPolicyTest {
 @Test void entryReentryOwnerAndChanceBoundary(){
  assertFalse(FlyByPresentationPolicy.entered(false,false,false,.1f,.01f));
  assertTrue(FlyByPresentationPolicy.entered(false,true,false,.1f,.01f));
  assertFalse(FlyByPresentationPolicy.entered(true,true,false,.1f,.01f));
  assertFalse(FlyByPresentationPolicy.entered(true,false,false,.1f,.01f));
  assertTrue(FlyByPresentationPolicy.entered(false,true,false,.1f,.01f));
  assertFalse(FlyByPresentationPolicy.entered(false,true,true,.1f,0f));
  assertTrue(FlyByPresentationPolicy.entered(false,true,false,.1f,.099999f));
  assertFalse(FlyByPresentationPolicy.entered(false,true,false,.1f,.1f));
  assertFalse(FlyByPresentationPolicy.entered(false,true,true,1f,0f));
 }
}

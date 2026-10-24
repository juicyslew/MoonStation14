function New-PopulationAnalyzerArguments {
    param(
        [string]$ServerPath,
        [string]$ClientPath,
        [string]$Scenario,
        [string]$Protocol,
        [bool]$RequireFlyerPose,
        [string]$OutputPath
    )
    $arguments = [Collections.Generic.List[string]]::new()
    $arguments.Add('--server'); $arguments.Add($ServerPath)
    $arguments.Add('--client'); $arguments.Add($ClientPath)
    $arguments.Add('--scenario'); $arguments.Add($Scenario)
    $arguments.Add('--protocol'); $arguments.Add($Protocol)
    if ($RequireFlyerPose) { $arguments.Add('--require-flyer-pose') }
    $arguments.Add('--output'); $arguments.Add($OutputPath)
    return ,$arguments.ToArray()
}

function Assert-ConnectedJoinParameters {
    param(
        [int]$WallTimeoutSeconds,
        [int]$PreparationTimeoutSeconds,
        [bool]$PopulationScenarioSpecified,
        [bool]$PopulationSustained,
        [bool]$FlyerPoseDiagnostic = $false,
        [string]$Scenario = ''
    )
    if ($FlyerPoseDiagnostic -and (!$PopulationScenarioSpecified -or $Scenario -ne 'Mixed')) { throw 'FlyerPoseDiagnostic requires PopulationScenario Mixed.' }
    if ($PopulationSustained -and !$PopulationScenarioSpecified) {
        throw 'PopulationSustained requires PopulationScenario.'
    }
    if ($PopulationSustained) {
        if ($WallTimeoutSeconds -lt 600 -or $WallTimeoutSeconds -gt 900) {
            throw 'Sustained capture WallTimeoutSeconds must be between 600 and 900.'
        }
    } elseif ($WallTimeoutSeconds -lt 30 -or $WallTimeoutSeconds -gt 120) {
        throw 'WallTimeoutSeconds must be between 30 and 120 for ordinary and stationary runs.'
    }
    if ($PreparationTimeoutSeconds -lt 30 -or $PreparationTimeoutSeconds -gt 600) {
        throw 'PreparationTimeoutSeconds must be between 30 and 600.'
    }
}

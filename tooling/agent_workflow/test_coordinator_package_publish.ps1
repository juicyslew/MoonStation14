$ErrorActionPreference='Stop'
$publisher=Join-Path $PSScriptRoot 'publish_workflow.ps1'
$base=Join-Path $env:LOCALAPPDATA 'Temp\opencode'
$fixture=Join-Path $base ('wr10-binding-publisher-' + [guid]::NewGuid().ToString('N'))
$main=Join-Path $fixture 'MoonStation14';$shared=Join-Path $fixture 'MoonStation14-shared'
$stage=Join-Path $base ('wr10-binding-stage-' + [guid]::NewGuid().ToString('N'))
$bindingPlanPath=$null;$success=$false
New-Item -ItemType Directory -Path $main,$shared -Force|Out-Null
try {
    $reviewerProfile=Join-Path $main '.opencode/agents/fidelity-reviewer.md';New-Item -ItemType Directory -Path (Split-Path $reviewerProfile) -Force|Out-Null
    $sourceProfile=Join-Path $PSScriptRoot '..\..\.opencode\agents\fidelity-reviewer.md'
    [IO.File]::Copy([IO.Path]::GetFullPath($sourceProfile),$reviewerProfile)
    $legacyEntry=[ordered]@{type='local';command=@((Join-Path $shared '.venv-workflow-mcp/Scripts/python.exe'),(Join-Path $shared 'tooling/agent_workflow/workflow_mcp.py'),'--repo',$main,'--profile','author');cwd=$main;environment=[ordered]@{};disabled=$false;codemode=$false;protocol='legacy';timeout=[ordered]@{startup=15000;catalog=15000;execution=300000}}
    $sourceConfig=Join-Path $PSScriptRoot '..\..\opencode.jsonc'
    $config=Get-Content -LiteralPath $sourceConfig -Raw|ConvertFrom-Json -AsHashtable
    $config.mcp.servers.ms14_workflow=$legacyEntry
    $config.mcp.servers.user_server=[ordered]@{command=@('keep')}
    $config.mcp.timeout=[ordered]@{catalog=777}
    $configPath=Join-Path $main 'opencode.jsonc';[IO.File]::WriteAllText($configPath,($config|ConvertTo-Json -Depth 50 -Compress),[Text.UTF8Encoding]::new($false))
    $token=Join-Path $fixture 'author.token';[IO.File]::WriteAllText($token,'fixture-token',[Text.UTF8Encoding]::new($false))
    $contextPath=Join-Path $fixture 'author-context.json';$context=@{repo=$main;authenticated_author=@{session_id='session-author';worktree=$main;role='author'}}
    [IO.File]::WriteAllText($contextPath,($context|ConvertTo-Json -Depth 10 -Compress),[Text.UTF8Encoding]::new($false))
    $coordinatorPath=Join-Path $fixture 'coordinator.json';$coordinator=@{database=(Join-Path $fixture 'db.sqlite');artifact_dir=(Join-Path $fixture 'artifacts');opencode_server='http://127.0.0.1:49100';bindings=@(@{caller='caller-author';token_file=$token;context_file=$contextPath});reviewers=@{
        fidelity=@{session_id='session-fidelity';worktree=$main;agent='fidelity-reviewer';token_file=(Join-Path $fixture 'review-token');caller='caller-fidelity'}
    }}
    [IO.File]::WriteAllText($coordinatorPath,($coordinator|ConvertTo-Json -Depth 20 -Compress),[Text.UTF8Encoding]::new($false))
    $sha={param($p)(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLowerInvariant()}
    $sharedLive=Join-Path $shared 'tooling/agent_workflow/workflow_coordinator.py';New-Item -ItemType Directory -Path (Split-Path $sharedLive) -Force|Out-Null
    [IO.File]::WriteAllText($sharedLive,'receipt-owned previous coordinator bytes',[Text.UTF8Encoding]::new($false))
    $receiptPath=Join-Path $fixture 'receipt.json';$receipt=@{main_root=$main;shared_root=$shared;source_hashes=@{};files=@(
        @{target='main';path='opencode.jsonc';new_sha256=(& $sha $configPath);deleted=$false},
        @{target='main';path='.opencode/agents/fidelity-reviewer.md';new_sha256=(& $sha $reviewerProfile);deleted=$false},
        @{target='shared';path='tooling/agent_workflow/workflow_coordinator.py';new_sha256=(& $sha $sharedLive);deleted=$false}
    )}
    [IO.File]::WriteAllText($receiptPath,($receipt|ConvertTo-Json -Depth 20 -Compress),[Text.UTF8Encoding]::new($false))
    $roles=@(
        @{role='author';agent='orchestrate-ms14';caller='caller-author';session_id='session-author';worktree=$main;token_file=$token},
        @{role='fidelity';agent='fidelity-reviewer';caller='caller-fidelity';session_id='session-fidelity';worktree=$main;token_file=(Join-Path $fixture 'review-token');profile_path='.opencode/agents/fidelity-reviewer.md'}
    )
    $bindingPlanPath=Join-Path $base ('wr10-binding-plan-' + [guid]::NewGuid().ToString('N') + '.json')
    [IO.File]::WriteAllText($bindingPlanPath,(@{version=1;targets=@(@{target='main';coordinator_config=$coordinatorPath;roles=$roles})}|ConvertTo-Json -Depth 30 -Compress),[Text.UTF8Encoding]::new($false))
    $beforeConfig=& $sha $configPath;$beforeProfile=& $sha $reviewerProfile
    $output=& $publisher -CoordinatorPackage -PackageStage $stage -PreviousReceipt $receiptPath -BindingPlan $bindingPlanPath -TestFixtureRoot $fixture 2>&1 | Out-String
    if($LASTEXITCODE){throw "Publisher fixture failed: $output"}
    if(((& $sha $configPath) -cne $beforeConfig) -or ((& $sha $reviewerProfile) -cne $beforeProfile)){throw 'Publisher mutated a fixture target during plan staging.'}
    $manifest=Get-Content (Join-Path $stage 'manifest.json') -Raw|ConvertFrom-Json
    if($manifest.bindings.status -cne 'planned-explicit-targets' -or $manifest.bindings.source_plan_sha256 -cne (& $sha $bindingPlanPath)){throw 'Publisher did not bind complete explicit-role plan.'}
    $bindingManifest=Get-Content (Join-Path $stage $manifest.bindings.manifest) -Raw|ConvertFrom-Json
    if(@($bindingManifest.files.PSObject.Properties).Count -ne 2){throw 'Expected staged OpenCode config and scoped profile plan.'}
    $newConfig=Get-Content (Join-Path $stage 'bindings/main/opencode.jsonc') -Raw|ConvertFrom-Json
    if($newConfig.mcp.servers.user_server.command[0] -cne 'keep' -or $newConfig.mcp.servers.ms14_workflow_review_parity.disabled -or $newConfig.mcp.servers.ms14_workflow_review_coverage){throw 'Target-specific MCP server merge leaked roles or changed unrelated servers.'}
    $permissions=@($newConfig.agents.'fidelity-reviewer'.permissions)
    foreach($tool in @('claim_task','complete_review','report_error','status')){if(@($permissions|Where-Object{$_.action -eq "ms14_workflow_review_fidelity_$tool" -and $_.resource -eq '*' -and $_.effect -eq 'allow'}).Count -ne 1){throw "Missing exact reviewer permission $tool"}}
    if(@($permissions|Where-Object{$_.action -eq 'ms14_workflow_*' -and $_.resource -eq '*' -and $_.effect -eq 'deny'}).Count -ne 1){throw 'Actual prefix deny policy was not preserved.'}
    if(@($permissions|Where-Object{$_.action -eq 'tool' -and $_.resource -like 'ms14_workflow*'}).Count){throw 'Generic tool permission schema leaked into coordinator config.'}
    $authorPermissions=@($newConfig.agents.'orchestrate-ms14'.permissions)
    foreach($tool in @('submit','status','acknowledge_result')){if(@($authorPermissions|Where-Object{$_.action -eq "ms14_workflow_$tool" -and $_.resource -eq '*' -and $_.effect -eq 'allow'}).Count -ne 1){throw "Missing exact author permission $tool"}}
    $install=Join-Path $PSScriptRoot 'install_workflow_coordinator.ps1'
    $stagedInstall=Join-Path $stage 'tooling/agent_workflow/install_workflow_coordinator.ps1'
    $python='C:\Users\William\Documents\Minecraft Mod Dev Files\MoonStation14-shared\.venv-workflow-mcp\Scripts\python.exe'
    $bindingEntry=$bindingManifest.files.'main/opencode.jsonc';$backupPath=Join-Path $stage $bindingEntry.backup_path
    $backupBytes=[IO.File]::ReadAllBytes($backupPath);[IO.File]::WriteAllBytes($backupPath,[byte[]](0,1,2))
    $refused=$false;try{& $install -VerifyOnly -PackageRoot $stage -PythonPath $python|Out-Null}catch{$refused=$true}
    if(-not $refused){throw 'Installer accepted a corrupted binding backup.'};[IO.File]::WriteAllBytes($backupPath,$backupBytes)
    $planBytes=[IO.File]::ReadAllBytes($bindingPlanPath);[IO.File]::WriteAllText($bindingPlanPath,'{}',[Text.UTF8Encoding]::new($false))
    $refused=$false;try{& $install -VerifyOnly -PackageRoot $stage -PythonPath $python|Out-Null}catch{$refused=$true}
    if(-not $refused){throw 'Installer accepted a changed binding plan.'};[IO.File]::WriteAllBytes($bindingPlanPath,$planBytes)
    $coordBytes=[IO.File]::ReadAllBytes($coordinatorPath);[IO.File]::AppendAllText($coordinatorPath,' ',[Text.UTF8Encoding]::new($false))
    $refused=$false;try{& $install -VerifyOnly -PackageRoot $stage -PythonPath $python|Out-Null}catch{$refused=$true}
    if(-not $refused){throw 'Installer accepted a changed coordinator source config.'};[IO.File]::WriteAllBytes($coordinatorPath,$coordBytes)
    $liveBytes=[IO.File]::ReadAllBytes($configPath);[IO.File]::AppendAllText($configPath,' ',[Text.UTF8Encoding]::new($false))
    $refused=$false;try{& $install -VerifyOnly -PackageRoot $stage -PythonPath $python|Out-Null}catch{$refused=$true}
    if(-not $refused){throw 'Installer accepted a stale live target.'};[IO.File]::WriteAllBytes($configPath,$liveBytes)
    & $install -VerifyOnly -PackageRoot $stage -PythonPath $python | Out-Null
    & $stagedInstall -VerifyOnly -PackageRoot $stage -PythonPath $python | Out-Null
    $sharedEntry=$manifest.files.'tooling/agent_workflow/workflow_coordinator.py'
    if(-not $sharedEntry.backup_path -or $sharedEntry.backup_sha256 -cne $sharedEntry.old_target_sha256){throw 'Shared replacement backup is not explicitly receipt-bound.'}
    $sharedBackup=Join-Path $stage $sharedEntry.backup_path;$backupBytes=[IO.File]::ReadAllBytes($sharedBackup)
    [IO.File]::WriteAllBytes($sharedBackup,[byte[]](0,1,2));$refused=$false
    try{& $install -VerifyOnly -PackageRoot $stage -PythonPath $python|Out-Null}catch{$refused=$true}
    if(-not $refused){throw 'Installer accepted tampered shared replacement backup.'}
    [IO.File]::WriteAllBytes($sharedBackup,$backupBytes);Remove-Item -LiteralPath $sharedBackup -Force;$refused=$false
    try{& $install -VerifyOnly -PackageRoot $stage -PythonPath $python|Out-Null}catch{$refused=$true}
    if(-not $refused){throw 'Installer accepted missing shared replacement backup.'}
    [IO.File]::WriteAllBytes($sharedBackup,$backupBytes)
    $createEntry=$manifest.files.PSObject.Properties.Value|Where-Object{$null -eq $_.old_target_sha256}|Select-Object -First 1
    if($createEntry.backup_path -or $createEntry.backup_sha256){throw 'Create target unexpectedly has a replacement backup.'}
    & $install -VerifyOnly -PackageRoot $stage -PythonPath $python | Out-Null
    $linkedStage=Join-Path $fixture 'stage-junction';New-Item -ItemType Junction -Path $linkedStage -Target $stage|Out-Null
    $refused=$false;try{& $stagedInstall -VerifyOnly -PackageRoot $linkedStage -PythonPath $python|Out-Null}catch{$refused=$true}
    Remove-Item -LiteralPath $linkedStage -Force
    if(-not $refused){throw 'Verifier accepted a package-root junction to an otherwise valid stage.'}
    $sharedDir=Split-Path -Parent $sharedLive;$heldDir=Join-Path $fixture 'held-shared-workflow';$foreignDir=Join-Path $fixture 'foreign-shared-sentinel'
    Move-Item -LiteralPath $sharedDir -Destination $heldDir
    New-Item -ItemType Directory -Path $foreignDir|Out-Null
    [IO.File]::WriteAllBytes((Join-Path $foreignDir 'workflow_coordinator.py'),[IO.File]::ReadAllBytes((Join-Path $heldDir 'workflow_coordinator.py')))
    New-Item -ItemType Junction -Path $sharedDir -Target $foreignDir|Out-Null
    try {
        $refused=$false;try{& $install -VerifyOnly -PackageRoot $stage -PythonPath $python|Out-Null}catch{$refused=$true}
        if(-not $refused){throw 'Verifier accepted a same-bytes foreign shared-target junction.'}
    }
    finally {
        Remove-Item -LiteralPath $sharedDir -Force
        Move-Item -LiteralPath $heldDir -Destination $sharedDir
    }
    $refused=$false;try{& $publisher -CoordinatorPackage -PackageStage $stage -PreviousReceipt $receiptPath -BindingPlan $bindingPlanPath -TestFixtureRoot $fixture -Apply|Out-Null}catch{$refused=$true}
    if(-not $refused){throw 'Package publisher accepted forbidden Apply.'}
    "Coordinator publisher private integration fixture PASS: copied source OpenCode config; exact legacy author and fidelity reviewer grants; backup tamper/missing, stale inputs, and -Apply rejected; create has no backup; package-root and same-bytes foreign live-target junctions rejected; zero target writes."
    $success=$true
}
finally {
    "Preserved private publisher evidence (success=$success): fixture=$fixture ; stage=$stage ; binding plan=$bindingPlanPath"
}

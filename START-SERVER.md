# BitLab start commands

```powershell
# PostgreSQL
& "$env:USERPROFILE\scoop\apps\postgresql\current\bin\pg_ctl.exe" -D "$env:USERPROFILE\scoop\persist\postgresql\data" start
```

```powershell
# QNX C VM
& "$env:USERPROFILE\VMs\bitlab-c-vm\qnx-vm.ps1" -Action start
```

```powershell
# Backend
cd bitlab-backend-qnx-vm
mvn -B -q -DskipTests package
java -jar target\app.jar --server.port=8080
```

```powershell
# Frontend
cd frontend-verilog-qnx-fledged
npm run dev -- --port 5173 --strictPort
```

```powershell
# Verilog / VHDL toolchain
wsl -d Ubuntu-BitLab --exec bash -c "iverilog -V | head -1; ghdl --version | head -1"
```

```powershell
# QNX C VM status
& "$env:USERPROFILE\VMs\bitlab-c-vm\qnx-vm.ps1" -Action status
```

```powershell
# Stop backend and frontend
Get-NetTCPConnection -LocalPort 8080,5173 -State Listen | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force }
```

```powershell
# Stop PostgreSQL
& "$env:USERPROFILE\scoop\apps\postgresql\current\bin\pg_ctl.exe" -D "$env:USERPROFILE\scoop\persist\postgresql\data" stop -m fast
```

```powershell
# Stop QNX C VM and WSL
& "$env:USERPROFILE\VMs\bitlab-c-vm\qnx-vm.ps1" -Action stop
wsl --shutdown
```

package com.example.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.example.R
import com.example.engine.ExecutionManager
import com.example.engine.ExecutionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class SmartClickerTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = scope.launch {
            ExecutionManager.executionStatus.collectLatest { status ->
                updateTileState(status)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        job?.cancel()
    }

    override fun onClick() {
        super.onClick()
        ExecutionManager.togglePlayPause(applicationContext)
        updateTileState(ExecutionManager.executionStatus.value)
    }

    private fun updateTileState(status: ExecutionStatus) {
        val tile = qsTile ?: return
        when (status) {
            ExecutionStatus.RUNNING -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Đang chạy (⏹)"
                tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_foreground)
            }
            ExecutionStatus.PAUSED -> {
                tile.state = Tile.STATE_ACTIVE
                tile.label = "Tạm dừng (⏸)"
                tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_foreground)
            }
            ExecutionStatus.IDLE -> {
                tile.state = Tile.STATE_INACTIVE
                tile.label = "Smart Clicker"
                tile.icon = Icon.createWithResource(this, R.drawable.ic_launcher_foreground)
            }
        }
        tile.updateTile()
    }
}

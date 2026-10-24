package ru.maxrubtsoff.bankagg.ui.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import ru.maxrubtsoff.bankagg.domain.model.CapturedEvent
import ru.maxrubtsoff.bankagg.domain.repository.EventRepository

class LogViewModel(
    events: EventRepository
) : ViewModel() {

    val recent: StateFlow<List<CapturedEvent>> =
        events.observeRecent(100).stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
        )
}

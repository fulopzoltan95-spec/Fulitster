package com.example.myapplicationasd

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.math.BigInteger

class PlayerViewModel : ViewModel() {


    private val _isPlaying = MutableLiveData(false)
     val _songTime = MutableLiveData<Long>()


    val isPlaying: LiveData<Boolean> get() = _isPlaying
    val  songTime: LiveData<Long> get() = _songTime

    fun setPlaying(value: Boolean) {
        _isPlaying.value = value
    }

    fun setProgress(value: Long) {
        _songTime.value = value
    }
}
package com.example.create_part2.db;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

/**
 * 备忘录ViewModel
 */
public class NoteViewModel extends AndroidViewModel {

    private NoteRepository repository;
    private LiveData<List<Note>> allNotes;

    public NoteViewModel(@NonNull Application application) {
        super(application);
        repository = new NoteRepository(application);
        allNotes = repository.getAllNotes();
    }

    // 获取所有备忘录 - 合并后的版本
    public LiveData<List<Note>> getAllNotes() {
        if (allNotes == null) {
            Log.w("NoteViewModel", "allNotes 为空，重新初始化");
            allNotes = repository.getAllNotes();
        }
        return allNotes;
    }

    // 根据ID获取备忘录
    public LiveData<Note> getNoteById(int id) {
        return repository.getNoteById(id);
    }

    // 获取所有带提醒的备忘录
    public LiveData<List<Note>> getNotesWithReminders() {
        return repository.getNotesWithReminders();
    }

    // 添加强制刷新方法
    public void refreshData() {
        if (repository != null) {
            repository.refreshAllData();
        }
    }

    // 插入备忘录
    public void insert(Note note) {
        repository.insert(note);
    }

    // 更新备忘录
    public void update(Note note) {
        repository.update(note);
    }

    // 删除备忘录
    public void delete(Note note) {
        repository.delete(note);
    }
}
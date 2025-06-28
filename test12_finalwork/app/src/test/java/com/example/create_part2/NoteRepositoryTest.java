package com.example.create_part2;

import android.content.Context;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.create_part2.database.NoteDbHelper;
import com.example.create_part2.db.NoteRepository;
import com.example.create_part2.models.Note;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class NoteRepositoryTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private NoteDbHelper db;
    private NoteRepository repository;
    private Note testNote;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, NoteDbHelper.class)
                .allowMainThreadQueries()
                .build();
        repository = new NoteRepository(db.noteDao());
        
        // 创建测试数据
        testNote = new Note();
        testNote.setTitle("测试标题");
        testNote.setContent("测试内容");
        testNote.setReminderTime(System.currentTimeMillis() + 3600000); // 一小时后
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void insertAndGetNote() throws InterruptedException {
        // 插入笔记
        repository.insert(testNote);

        // 获取所有笔记
        CountDownLatch latch = new CountDownLatch(1);
        List<Note> notes = null;
        repository.getAllNotes().observeForever(notesList -> {
            notes = notesList;
            latch.countDown();
        });

        // 等待数据加载
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(notes);
        assertEquals(1, notes.size());
        assertEquals(testNote.getTitle(), notes.get(0).getTitle());
        assertEquals(testNote.getContent(), notes.get(0).getContent());
    }

    @Test
    public void updateNote() throws InterruptedException {
        // 插入笔记
        repository.insert(testNote);

        // 更新笔记
        testNote.setTitle("更新后的标题");
        testNote.setContent("更新后的内容");
        repository.update(testNote);

        // 获取更新后的笔记
        CountDownLatch latch = new CountDownLatch(1);
        List<Note> notes = null;
        repository.getAllNotes().observeForever(notesList -> {
            notes = notesList;
            latch.countDown();
        });

        // 等待数据加载
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(notes);
        assertEquals(1, notes.size());
        assertEquals("更新后的标题", notes.get(0).getTitle());
        assertEquals("更新后的内容", notes.get(0).getContent());
    }

    @Test
    public void deleteNote() throws InterruptedException {
        // 插入笔记
        repository.insert(testNote);

        // 删除笔记
        repository.delete(testNote);

        // 获取所有笔记
        CountDownLatch latch = new CountDownLatch(1);
        List<Note> notes = null;
        repository.getAllNotes().observeForever(notesList -> {
            notes = notesList;
            latch.countDown();
        });

        // 等待数据加载
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(notes);
        assertTrue(notes.isEmpty());
    }

    @Test
    public void getNoteById() throws InterruptedException {
        // 插入笔记
        repository.insert(testNote);

        // 获取指定笔记
        CountDownLatch latch = new CountDownLatch(1);
        Note note = null;
        repository.getNoteById(testNote.getId()).observeForever(noteResult -> {
            note = noteResult;
            latch.countDown();
        });

        // 等待数据加载
        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(note);
        assertEquals(testNote.getTitle(), note.getTitle());
        assertEquals(testNote.getContent(), note.getContent());
    }
} 
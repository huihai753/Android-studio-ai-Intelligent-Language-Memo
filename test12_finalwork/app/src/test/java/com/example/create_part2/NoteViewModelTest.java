package com.example.create_part2;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.create_part2.db.NoteViewModel;
import com.example.create_part2.models.Note;
import com.example.create_part2.repository.NoteRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(AndroidJUnit4.class)
public class NoteViewModelTest {
    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private NoteRepository repository;

    private NoteViewModel viewModel;
    private Note testNote;
    private List<Note> testNotes;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        viewModel = new NoteViewModel(repository);

        // 创建测试数据
        testNote = new Note();
        testNote.setId(1);
        testNote.setTitle("测试标题");
        testNote.setContent("测试内容");
        testNote.setReminderTime(System.currentTimeMillis() + 3600000);

        Note testNote2 = new Note();
        testNote2.setId(2);
        testNote2.setTitle("测试标题2");
        testNote2.setContent("测试内容2");
        testNote2.setReminderTime(System.currentTimeMillis() + 7200000);

        testNotes = Arrays.asList(testNote, testNote2);

        // 设置模拟行为
        MutableLiveData<List<Note>> notesLiveData = new MutableLiveData<>(testNotes);
        when(repository.getAllNotes()).thenReturn(notesLiveData);

        MutableLiveData<Note> noteLiveData = new MutableLiveData<>(testNote);
        when(repository.getNoteById(1)).thenReturn(noteLiveData);
    }

    @Test
    public void getAllNotes() {
        LiveData<List<Note>> notes = viewModel.getAllNotes();
        assertNotNull(notes);
        assertEquals(testNotes, notes.getValue());
    }

    @Test
    public void getNoteById() {
        LiveData<Note> note = viewModel.getNoteById(1);
        assertNotNull(note);
        assertEquals(testNote, note.getValue());
    }

    @Test
    public void insertNote() {
        Note newNote = new Note();
        newNote.setTitle("新笔记");
        newNote.setContent("新内容");

        doAnswer(invocation -> {
            Note note = invocation.getArgument(0);
            testNotes.add(note);
            return null;
        }).when(repository).insert(any(Note.class));

        viewModel.insert(newNote);
        verify(repository).insert(newNote);
    }

    @Test
    public void updateNote() {
        testNote.setTitle("更新后的标题");
        testNote.setContent("更新后的内容");

        doAnswer(invocation -> {
            Note note = invocation.getArgument(0);
            int index = testNotes.indexOf(note);
            if (index != -1) {
                testNotes.set(index, note);
            }
            return null;
        }).when(repository).update(any(Note.class));

        viewModel.update(testNote);
        verify(repository).update(testNote);
    }

    @Test
    public void deleteNote() {
        doAnswer(invocation -> {
            Note note = invocation.getArgument(0);
            testNotes.remove(note);
            return null;
        }).when(repository).delete(any(Note.class));

        viewModel.delete(testNote);
        verify(repository).delete(testNote);
    }
} 
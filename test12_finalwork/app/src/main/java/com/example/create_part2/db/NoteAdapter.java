package com.example.create_part2.db;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.create_part2.R;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 备忘录适配器
 * 该类是 RecyclerView 的适配器，用于将 Note 数据绑定到列表项视图上，实现笔记的显示和点击事件。
 * 主要负责笔记列表的数据显示、点击事件处理、数据差异比对等。
 */
public class NoteAdapter extends ListAdapter<Note, NoteAdapter.NoteViewHolder> {

    private static final String TAG = "NoteAdapter";
    private OnItemClickListener listener;

    public NoteAdapter() {
        super(new DiffUtil.ItemCallback<Note>() {
            @Override
            public boolean areItemsTheSame(@NonNull Note oldItem, @NonNull Note newItem) {
                return oldItem.getId() == newItem.getId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull Note oldItem, @NonNull Note newItem) {
                return oldItem.getTitle().equals(newItem.getTitle()) &&
                        oldItem.getContent().equals(newItem.getContent()) &&
                        oldItem.getCreatedTime() == newItem.getCreatedTime() &&
                        oldItem.getReminderTime() == newItem.getReminderTime();
            }
        });
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.note_item, parent, false);
        return new NoteViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Note currentNote = getItem(position);
        Log.d(TAG, "绑定第 " + position + " 项: ID=" + currentNote.getId() + ", 标题=" + currentNote.getTitle());

        holder.textViewTitle.setText(currentNote.getTitle());
        holder.textViewContent.setText(currentNote.getContent());

        // 格式化创建时间
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        String formattedDate = sdf.format(new Date(currentNote.getCreatedTime()));
        holder.textViewDate.setText(formattedDate);

        // 显示提醒时间（如果有）
        if (currentNote.getReminderTime() > 0) {
            holder.textViewReminder.setVisibility(View.VISIBLE);
            String reminderDate = sdf.format(new Date(currentNote.getReminderTime()));
            holder.textViewReminder.setText("提醒时间: " + reminderDate);

            // 检查提醒是否已触发，改变背景颜色
            if (currentNote.isReminderTriggered() || System.currentTimeMillis() >= currentNote.getReminderTime()) {
                // 提醒时间已到，显示红色背景
                holder.textViewReminder.setBackgroundResource(R.drawable.reminder_background_red);
            } else {
                // 提醒时间未到，显示绿色背景
                holder.textViewReminder.setBackgroundResource(R.drawable.reminder_background);
            }
        } else {
            holder.textViewReminder.setVisibility(View.GONE);
        }
    }

    @Override
    public void submitList(List<Note> list) {
        Log.d(TAG, "submitList 被调用，列表大小: " + (list != null ? list.size() : 0));
        super.submitList(list);
    }

    @Override
    public int getItemCount() {
        int count = super.getItemCount();
        Log.d(TAG, "getItemCount: " + count);
        return count;
    }

    public Note getNoteAt(int position) {
        return getItem(position);
    }

    class NoteViewHolder extends RecyclerView.ViewHolder {
        private TextView textViewTitle;
        private TextView textViewContent;
        private TextView textViewDate;
        private TextView textViewReminder;

        public NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewTitle = itemView.findViewById(R.id.text_view_title);
            textViewContent = itemView.findViewById(R.id.text_view_content);
            textViewDate = itemView.findViewById(R.id.text_view_date);
            textViewReminder = itemView.findViewById(R.id.text_view_reminder);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(getItem(position));
                }
            });
        }
    }

    public interface OnItemClickListener {
        void onItemClick(Note note);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }
}
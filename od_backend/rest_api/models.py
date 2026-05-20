import secrets
from django.db import models
from django.contrib.auth.models import User
from django.core.validators import MinValueValidator, MaxValueValidator


class AuthToken(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='auth_tokens')
    key = models.CharField(max_length=64, unique=True)
    created_at = models.DateTimeField(auto_now_add=True)
    expires_at = models.DateTimeField(null=True, blank=True)
    is_active = models.BooleanField(default=True)

    @classmethod
    def generate_key(cls):
        return secrets.token_hex(32)

    def __str__(self):
        return f'{self.user.username} — {self.key[:8]}...'


class UserProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name='profile')
    name = models.CharField(max_length=255)
    avatar_url = models.CharField(max_length=500, blank=True, null=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return self.name


class Category(models.Model):
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='categories')
    name = models.CharField(max_length=100)
    icon = models.CharField(max_length=50, blank=True, null=True)
    color = models.CharField(max_length=20, blank=True, null=True)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        unique_together = ('user', 'name')

    def __str__(self):
        return self.name



class Goal(models.Model):
    GOAL_TYPE_CHOICES = [
        ('daily', 'Daily'),
        ('weekly', 'Weekly'),
        ('monthly', 'Monthly'),
        ('custom', 'Custom'),
    ]
    FREQUENCY_CHOICES = [
        ('once', 'Once'),
        ('daily', 'Daily'),
        ('weekly', 'Weekly'),
        ('monthly', 'Monthly'),
    ]
    STATUS_CHOICES = [
        ('planned', 'Planned'),
        ('in_progress', 'In Progress'),
        ('completed', 'Completed'),
        ('partial', 'Partial'),
        ('failed', 'Failed'),
        ('archived', 'Archived'),
    ]

    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='goals')
    parent_goal = models.ForeignKey(
        'self', on_delete=models.SET_NULL, null=True, blank=True, related_name='subgoals'
    )
    title = models.CharField(max_length=255)
    description = models.TextField(blank=True, null=True)
    category = models.ForeignKey(
        Category, on_delete=models.SET_NULL, null=True, blank=True, related_name='goals'
    )
    goal_type = models.CharField(max_length=20, choices=GOAL_TYPE_CHOICES, default='daily')
    frequency = models.CharField(max_length=20, choices=FREQUENCY_CHOICES, default='once')
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='planned')
    start_date = models.DateField(null=True, blank=True)
    end_date = models.DateField(null=True, blank=True)
    deadline = models.DateField(null=True, blank=True)
    progress_percent = models.FloatField(
        default=0,
        validators=[MinValueValidator(0), MaxValueValidator(100)],
    )
    target_value = models.FloatField(null=True, blank=True)
    current_value = models.FloatField(null=True, blank=True)
    is_active = models.BooleanField(default=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return self.title


class DayEntry(models.Model):
    STATUS_CHOICES = [
        ('empty', 'Empty'),
        ('planned', 'Planned'),
        ('in_progress', 'In Progress'),
        ('completed', 'Completed'),
        ('partial', 'Partial'),
        ('failed', 'Failed'),
    ]

    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='day_entries')
    date = models.DateField()
    main_goal = models.ForeignKey(
        Goal, on_delete=models.SET_NULL, null=True, blank=True, related_name='main_day_entries'
    )
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='empty')
    progress_percent = models.FloatField(
        default=0,
        validators=[MinValueValidator(0), MaxValueValidator(100)],
    )
    dedication_minutes = models.IntegerField(null=True, blank=True)
    result_text = models.TextField(blank=True, null=True)
    reflection_text = models.TextField(blank=True, null=True)
    failure_reason = models.TextField(blank=True, null=True)
    is_closed = models.BooleanField(default=False)
    closed_at = models.DateTimeField(null=True, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        unique_together = ('user', 'date')

    def __str__(self):
        return f'{self.user.username} — {self.date}'



class Activity(models.Model):
    ACTIVITY_TYPE_CHOICES = [
        ('task', 'Task'),
        ('session', 'Session'),
        ('habit', 'Habit'),
        ('event', 'Event'),
        ('deep_work', 'Deep Work'),
    ]
    STATUS_CHOICES = [
        ('pending', 'Pending'),
        ('in_progress', 'In Progress'),
        ('completed', 'Completed'),
        ('partial', 'Partial'),
        ('failed', 'Failed'),
    ]

    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='activities')
    day_entry = models.ForeignKey(
        DayEntry, on_delete=models.CASCADE, null=True, blank=True, related_name='activities'
    )
    goal = models.ForeignKey(
        Goal, on_delete=models.SET_NULL, null=True, blank=True, related_name='activities'
    )
    category = models.ForeignKey(
        Category, on_delete=models.SET_NULL, null=True, blank=True, related_name='activities'
    )
    title = models.CharField(max_length=255)
    description = models.TextField(blank=True, null=True)
    start_time = models.TimeField(null=True, blank=True)
    end_time = models.TimeField(null=True, blank=True)
    estimated_minutes = models.IntegerField(null=True, blank=True)
    actual_minutes = models.IntegerField(null=True, blank=True)
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='pending')
    activity_type = models.CharField(max_length=20, choices=ACTIVITY_TYPE_CHOICES, default='task')
    order = models.IntegerField(default=0)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return self.title

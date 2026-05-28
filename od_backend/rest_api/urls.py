from django.urls import path
from . import views

urlpatterns = [
    path('auth/register/', views.RegisterView.as_view(), name='auth_register'),
    path('auth/login/', views.LoginView.as_view(), name='auth_login'),
    path('auth/logout/', views.LogoutView.as_view(), name='auth_logout'),
    path('auth/me/', views.MeView.as_view(), name='auth_me'),
    path('profile/', views.ProfileView.as_view(), name='profile_view'),


    path('goals/', views.GoalsListView.as_view(), name='goals_list'),
    path('goals/<int:id>/', views.GoalsDetailView.as_view(), name='goals_detail'),

    path('day-entries/', views.DayEntriesListView.as_view(), name='day_entries_list'),
    path('day-entries/today/', views.DayEntriesTodayView.as_view(), name='day_entries_today'),
    path('day-entries/<int:id>/', views.DayEntriesItemView.as_view(), name='day_entries_item'),
    path('day-entries/<int:id>/close/', views.DayEntriesCloseView.as_view(), name='day_entries_close'),
    path('day-entries/<int:id>/draft-close/', views.DayEntriesDraftCloseView.as_view(), name='day_entries_draft_close'),
    path('day-entries/<int:id>/activities/', views.DayEntryActivitiesView.as_view(), name='day_entry_activities'),
    path('day-entries/<int:id>/detail/', views.DayEntriesDetailView.as_view(), name='day_entries_detail_view'),
    path('day-entries/<int:id>/reopen/', views.DayEntriesReopenView.as_view(), name='day_entries_reopen'),

    path('activities/', views.ActivitiesListView.as_view(), name='activities_list'),
    path('activities/<int:id>/', views.ActivitiesDetailView.as_view(), name='activities_detail'),

    path('stats/summary/', views.StatsSummaryView.as_view(), name='stats_summary'),
    path('stats/streak/', views.StatsStreakView.as_view(), name='stats_streak'),
    path('stats/weekly/', views.StatsWeeklyView.as_view(), name='stats_weekly'),

    path('dashboard/today/', views.DashboardTodayView.as_view(), name='dashboard_today'),

    path('calendar/', views.CalendarView.as_view(), name='calendar_view'),
]

from django.urls import path
from . import views

urlpatterns = [
    path('auth/register/', views.RegisterView.as_view(), name='auth_register'),
    path('auth/login/', views.LoginView.as_view(), name='auth_login'),
    path('auth/logout/', views.LogoutView.as_view(), name='auth_logout'),
    path('auth/me/', views.MeView.as_view(), name='auth_me'),
    path('profile/', views.ProfileView.as_view(), name='profile_view'),
    path('categories/', views.CategoriesListView.as_view(), name='categories_list'),
    path('categories/<int:id>/', views.CategoriesDetailView.as_view(), name='categories_detail'),
    path('tags/', views.TagsListView.as_view(), name='tags_list'),
    path('tags/<int:id>/', views.TagsDetailView.as_view(), name='tags_detail'),
    path('goals/', views.GoalsListView.as_view(), name='goals_list'),
    path('goals/<int:id>/', views.GoalsDetailView.as_view(), name='goals_detail'),
    path('recurrence-rules/', views.RecurrenceRulesListView.as_view(), name='recurrence_rules_list'),
    path('recurrence-rules/<int:id>/', views.RecurrenceRulesDetailView.as_view(), name='recurrence_rules_detail'),
    path('activity-templates/', views.ActivityTemplatesListView.as_view(), name='activity_templates_list'),
    path('activity-templates/<int:id>/', views.ActivityTemplatesDetailView.as_view(), name='activity_templates_detail'),
    path('day-entries/', views.DayEntriesListView.as_view(), name='day_entries_list'),
    path('day-entries/today/', views.DayEntriesTodayView.as_view(), name='day_entries_today'),
    path('day-entries/<int:id>/', views.DayEntriesItemView.as_view(), name='day_entries_item'),
]

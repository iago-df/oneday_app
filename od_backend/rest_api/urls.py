from django.urls import path
from . import views

urlpatterns = [
    path('auth/register/', views.RegisterView.as_view(), name='auth_register'),
    path('auth/login/', views.LoginView.as_view(), name='auth_login'),
    path('auth/logout/', views.LogoutView.as_view(), name='auth_logout'),
    path('auth/me/', views.MeView.as_view(), name='auth_me'),
    path('profile/', views.ProfileView.as_view(), name='profile_view'),
]

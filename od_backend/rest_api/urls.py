from django.urls import path
from . import views

urlpatterns = [
    path('auth/register/', views.register, name='auth_register'),
    path('auth/login/', views.login, name='auth_login'),
    path('auth/logout/', views.auth_logout, name='auth_logout'),
]

import json

from django.contrib.auth import authenticate
from django.http import JsonResponse
from django.utils.decorators import method_decorator
from django.views.decorators.csrf import csrf_exempt
from django.contrib.auth.models import User

from .helpers import get_authenticated_user
from .models import AuthToken, UserProfile



class AuthMixin:
    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        self.user = None

    @method_decorator(csrf_exempt)
    def dispatch(self, request, *args, **kwargs):
        self.user, err = get_authenticated_user(request)
        if err:
            return err
        return super().dispatch(request, *args, **kwargs)

@method_decorator(csrf_exempt, name='dispatch')
class RegisterView(View):
    def post(self, request):
        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        username = data.get('username', '').strip()
        email = data.get('email', '').strip()
        password = data.get('password', '')
        name = data.get('name', '').strip()

        if not username:
            return JsonResponse({'error': 'username is required'}, status=400)
        if not password:
            return JsonResponse({'error': 'password is required'}, status=400)

        if User.objects.filter(username=username).exists():
            return JsonResponse({'error': 'Username already taken'}, status=409)

        if email and User.objects.filter(email=email).exists():
            return JsonResponse({'error': 'Email already in use'}, status=409)

        user = User.objects.create_user(username=username, password=password, email=email)
        UserProfile.objects.create(user=user, name=name or username)

        token = AuthToken.objects.create(user=user, key=AuthToken.generate_key())

        return JsonResponse({
            'token': token.key,
            'user': {
                'id': user.id,
                'username': user.username,
                'email': user.email,
                'name': name or username,
            },
        }, status=201)


@method_decorator(csrf_exempt, name='dispatch')
class LoginView(View):
    def post(self, request):
        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        username = data.get('username', '')
        password = data.get('password', '')

        if not username or not password:
            return JsonResponse({'error': 'username and password are required'}, status=400)

        user = authenticate(username=username, password=password)
        if not user:
            return JsonResponse({'error': 'Invalid credentials'}, status=401)

        token = AuthToken.objects.create(user=user, key=AuthToken.generate_key())

        profile = getattr(user, 'profile', None)

        return JsonResponse({
            'token': token.key,
            'user': {
                'id': user.id,
                'username': user.username,
                'email': user.email,
                'name': profile.name if profile else user.username,
            },
        })


class LogoutView(AuthMixin, View):
    def post(self, request):
        AuthToken.objects.filter(user=self.user, is_active=True).update(is_active=False)
        return JsonResponse({'message': 'Logged out'})


class MeView(AuthMixin, View):
    def get(self, request):
        try:
            profile = self.user.profile
            profile_data = {'name': profile.name, 'avatar_url': profile.avatar_url}
        except UserProfile.DoesNotExist:
            profile_data = {'name': self.user.username, 'avatar_url': None}

        return JsonResponse({
            'id': self.user.id,
            'username': self.user.username,
            'email': self.user.email,
            'name': profile_data['name'],
            'avatar_url': profile_data['avatar_url'],
        })
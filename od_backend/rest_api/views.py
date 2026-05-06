import json

from django.contrib.auth import authenticate
from django.http import JsonResponse
from django.utils.decorators import method_decorator
from django.views import View
from django.views.decorators.csrf import csrf_exempt
from django.contrib.auth.models import User

from .helpers import get_authenticated_user
from .models import AuthToken, UserProfile, Category


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


class ProfileView(AuthMixin, View):
    def _profile_json(self, profile):
        return {
            'id': profile.id,
            'username': self.user.username,
            'email': self.user.email,
            'name': profile.name,
            'avatar_url': profile.avatar_url,
            'created_at': profile.created_at.isoformat(),
            'updated_at': profile.updated_at.isoformat(),
        }

    def get(self, request):
        profile, _ = UserProfile.objects.get_or_create(user=self.user, defaults={'name': self.user.username})
        return JsonResponse(self._profile_json(profile))


    def put(self, request):
        profile, _ = UserProfile.objects.get_or_create(user=self.user, defaults={'name': self.user.username})

        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        if 'name' in data:
            name = data['name'].strip()
            if not name:
                return JsonResponse({'error': 'name cannot be empty'}, status=400)
            profile.name = name

        if 'avatar_url' in data:
            profile.avatar_url = data['avatar_url'] or None

        if 'email' in data:
            email = data['email'].strip()
            if email and User.objects.filter(email=email).exclude(id=self.user.id).exists():
                return JsonResponse({'error': 'Email already in use'}, status=409)
            self.user.email = email
            self.user.save(update_fields=['email'])

        profile.save()
        profile.refresh_from_db()
        return JsonResponse(self._profile_json(profile))



def _category_json(c):
    return {
        'id': c.id,
        'name': c.name,
        'icon': c.icon,
        'color': c.color,
        'is_active': c.is_active,
        'created_at': c.created_at.isoformat(),
        'updated_at': c.updated_at.isoformat(),
    }


class CategoriesListView(AuthMixin, View):
    def get(self, request):
        qs = Category.objects.filter(user=self.user).order_by('name')
        return JsonResponse({'categories': [_category_json(c) for c in qs]})


    def post(self, request):
        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        name = data.get('name', '').strip()
        if not name:
            return JsonResponse({'error': 'name is required'}, status=400)

        if Category.objects.filter(user=self.user, name=name).exists():
            return JsonResponse({'error': 'Category with this name already exists'}, status=409)

        category = Category.objects.create(
            user=self.user,
            name=name,
            icon=data.get('icon') or None,
            color=data.get('color') or None,
            is_active=data.get('is_active', True),
        )
        return JsonResponse(_category_json(category), status=201)


class CategoriesDetailView(AuthMixin, View):
    def _get_category(self, id):
        try:
            return Category.objects.get(id=id, user=self.user), None
        except Category.DoesNotExist:
            return None, JsonResponse({'error': 'Category not found'}, status=404)

    def get(self, request, id):
        category, err = self._get_category(id)
        if err:
            return err
        return JsonResponse(_category_json(category))


    def put(self, request, id):
        category, err = self._get_category(id)
        if err:
            return err

        try:
            data = json.loads(request.body)
        except (json.JSONDecodeError, ValueError):
            return JsonResponse({'error': 'Invalid JSON'}, status=400)

        if 'name' in data:
            name = data['name'].strip()
            if not name:
                return JsonResponse({'error': 'name cannot be empty'}, status=400)
            if Category.objects.filter(user=self.user, name=name).exclude(id=id).exists():
                return JsonResponse({'error': 'Category with this name already exists'}, status=409)
            category.name = name

        if 'icon' in data:
            category.icon = data['icon'] or None
        if 'color' in data:
            category.color = data['color'] or None
        if 'is_active' in data:
            category.is_active = bool(data['is_active'])

        category.save()
        return JsonResponse(_category_json(category))


    def delete(self, request, id):
        category, err = self._get_category(id)
        if err:
            return err
        category.delete()
        return JsonResponse({'message': 'Category deleted'})
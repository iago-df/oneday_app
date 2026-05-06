import json

from django.contrib.auth import authenticate
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.contrib.auth.models import User
from .models import AuthToken, UserProfile



@csrf_exempt
def register(request):
    if request.method != 'POST':
        return JsonResponse({'error': 'Method not allowed'}, status=405)

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


@csrf_exempt
def login(request):
    if request.method != 'POST':
        return JsonResponse({'error': 'Method not allowed'}, status=405)

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
